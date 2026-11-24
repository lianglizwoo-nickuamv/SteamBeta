package com.example.steambeta.backend.exchange;

import com.example.steambeta.backend.auth.AccountService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampusExchangeService {
    public record ExchangeItem(
            UUID id,
            UUID ownerAccountId,
            String ownerAlias,
            UUID communityId,
            String title,
            String description,
            String category, // BOOK, CALCULATOR, LAB_EQUIPMENT, NOTES, TOOL, OTHER
            String exchangeType, // GIFT, LOAN, EXCHANGE
            String status, // AVAILABLE, RESERVED, COMPLETED, REPORTED
            String contactInfo,
            Instant createdAt,
            long attachmentCount
    ) {}

    public record ExchangeDraft(
            UUID communityId,
            String title,
            String description,
            String category,
            String exchangeType,
            String contactInfo
    ) {}

    public record ExchangeUpdate(
            String title,
            String description,
            String category,
            String exchangeType,
            String status,
            String contactInfo
    ) {}

    public record StatusUpdate(String status) {}

    public record AttachmentItem(
            UUID id,
            UUID exchangeItemId,
            String originalFilename,
            String extension,
            String mimeType,
            long fileSizeBytes,
            Instant createdAt
    ) {}

    public record AttachmentDownload(
            String originalFilename,
            String mimeType,
            long fileSizeBytes,
            Resource resource
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final ExchangeStorageService storage;

    public CampusExchangeService(JdbcTemplate jdbc, AccountService accounts, ExchangeStorageService storage) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.storage = storage;
    }

    public List<ExchangeItem> list(String categoryFilter, boolean mineOnly) {
        UUID actorId = accounts.current().accountId();
        String cat = categoryFilter == null || categoryFilter.isBlank() ? null : categoryFilter.strip().toUpperCase(Locale.ROOT);
        return jdbc.query(
                "select * from replica.list_exchange_items_v15(?::uuid, ?::text, ?::boolean)",
                (rs, row) -> mapItem(rs),
                actorId, cat, mineOnly
        );
    }

    @Transactional
    public ExchangeItem create(ExchangeDraft draft) {
        if (draft == null || draft.title() == null || draft.contactInfo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completa el título y la información de contacto.");
        }
        UUID ownerId = accounts.current().accountId();
        String cat = draft.category() == null ? "BOOK" : draft.category().strip().toUpperCase(Locale.ROOT);
        String type = draft.exchangeType() == null ? "EXCHANGE" : draft.exchangeType().strip().toUpperCase(Locale.ROOT);

        return jdbc.queryForObject(
                "select * from replica.create_exchange_item_v15(?::uuid, ?::text, ?::text, ?::text, ?::text, ?::text)",
                (rs, row) -> mapItem(rs),
                ownerId, draft.title().strip(), draft.description(), cat, type, draft.contactInfo().strip()
        );
    }

    @Transactional
    public ExchangeItem update(UUID itemId, ExchangeUpdate update) {
        if (update == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de actualización están vacíos.");
        }
        UUID ownerId = accounts.current().accountId();
        return jdbc.queryForObject(
                "select * from replica.update_exchange_item_v15(?::uuid, ?::uuid, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text)",
                (rs, row) -> mapItem(rs),
                ownerId,
                itemId,
                update.title(),
                update.description(),
                update.category() == null ? null : update.category().strip().toUpperCase(Locale.ROOT),
                update.exchangeType() == null ? null : update.exchangeType().strip().toUpperCase(Locale.ROOT),
                update.status() == null ? null : update.status().strip().toUpperCase(Locale.ROOT),
                update.contactInfo()
        );
    }

    @Transactional
    public void updateStatus(UUID itemId, StatusUpdate update) {
        if (update == null || update.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido.");
        }
        update(itemId, new ExchangeUpdate(null, null, null, null, update.status(), null));
    }

    @Transactional
    public void delete(UUID itemId) {
        UUID ownerId = accounts.current().accountId();
        List<String> keys = jdbc.query(
                "select storage_key from replica.delete_exchange_item_v15(?::uuid, ?::uuid)",
                (rs, row) -> rs.getString("storage_key"),
                ownerId, itemId
        );
        deleteFilesAfterCommit(keys);
    }

    public List<AttachmentItem> listAttachments(UUID itemId) {
        UUID actorId = accounts.current().accountId();
        return jdbc.query(
                "select * from replica.list_exchange_attachments_v15(?::uuid, ?::uuid)",
                (rs, row) -> mapAttachment(rs),
                actorId, itemId
        );
    }

    @Transactional
    public AttachmentItem uploadAttachment(UUID itemId, MultipartFile file) {
        UUID actorId = accounts.current().accountId();
        ExchangeStorageService.StoredFile stored = storage.store(file);
        try {
            return jdbc.queryForObject(
                    "select * from replica.add_exchange_attachment_v15(?::uuid, ?::uuid, ?::text, ?::text, ?::text, ?::text, ?::bigint)",
                    (rs, row) -> mapAttachment(rs),
                    actorId, itemId, stored.storageKey(), stored.originalFilename(),
                    stored.extension(), stored.mimeType(), stored.sizeBytes()
            );
        } catch (RuntimeException ex) {
            // Compensación inmediata: eliminar archivo físico para no dejar huérfanos
            storage.deleteQuietly(stored.storageKey());
            throw ex;
        }
    }

    public AttachmentDownload downloadAttachment(UUID attachmentId) {
        UUID actorId = accounts.current().accountId();
        var rows = jdbc.query(
                "select * from replica.read_exchange_attachment_v15(?::uuid, ?::uuid)",
                (rs, row) -> new Object[] {
                        rs.getString("storage_key"),
                        rs.getString("original_filename"),
                        rs.getString("mime_type"),
                        rs.getLong("file_size_bytes")
                },
                actorId, attachmentId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo adjunto no encontrado.");
        }
        Object[] row = rows.getFirst();
        Resource resource = storage.loadAsResource((String) row[0]);
        return new AttachmentDownload((String) row[1], (String) row[2], (Long) row[3], resource);
    }

    @Transactional
    public void deleteAttachment(UUID attachmentId) {
        UUID actorId = accounts.current().accountId();
        String removedKey = jdbc.queryForObject(
                "select replica.delete_exchange_attachment_v15(?::uuid, ?::uuid)",
                String.class,
                actorId, attachmentId
        );
        if (removedKey != null && !removedKey.isBlank()) {
            deleteFilesAfterCommit(List.of(removedKey));
        }
    }

    private void deleteFilesAfterCommit(List<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) return;
        List<String> keysCopy = List.copyOf(storageKeys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String key : keysCopy) {
                        storage.deleteQuietly(key);
                    }
                }
            });
        } else {
            for (String key : keysCopy) {
                storage.deleteQuietly(key);
            }
        }
    }

    private static ExchangeItem mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExchangeItem(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_account_id", UUID.class),
                rs.getString("owner_alias"),
                rs.getObject("community_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("exchange_type"),
                rs.getString("status"),
                rs.getString("contact_info"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("attachment_count")
        );
    }

    private static AttachmentItem mapAttachment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AttachmentItem(
                rs.getObject("id", UUID.class),
                rs.getObject("exchange_item_id", UUID.class),
                rs.getString("original_filename"),
                rs.getString("extension"),
                rs.getString("mime_type"),
                rs.getLong("file_size_bytes"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
