package com.example.steambeta.backend.exchange;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/exchange")
public class CampusExchangeController {
    private final CampusExchangeService exchangeService;

    public CampusExchangeController(CampusExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/items")
    public List<CampusExchangeService.ExchangeItem> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean mine) {
        return exchangeService.list(category, mine);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CampusExchangeService.ExchangeItem create(@RequestBody CampusExchangeService.ExchangeDraft draft) {
        return exchangeService.create(draft);
    }

    @PatchMapping("/items/{itemId}")
    public CampusExchangeService.ExchangeItem update(@PathVariable UUID itemId,
                                                     @RequestBody CampusExchangeService.ExchangeUpdate update) {
        return exchangeService.update(itemId, update);
    }

    @PatchMapping("/items/{itemId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable UUID itemId, @RequestBody CampusExchangeService.StatusUpdate update) {
        exchangeService.updateStatus(itemId, update);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID itemId) {
        exchangeService.delete(itemId);
    }

    @GetMapping("/items/{itemId}/attachments")
    public List<CampusExchangeService.AttachmentItem> listAttachments(@PathVariable UUID itemId) {
        return exchangeService.listAttachments(itemId);
    }

    @PostMapping(value = "/items/{itemId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CampusExchangeService.AttachmentItem uploadAttachment(@PathVariable UUID itemId,
                                                                 @RequestParam("file") MultipartFile file) {
        return exchangeService.uploadAttachment(itemId, file);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID attachmentId) {
        var dl = exchangeService.downloadAttachment(attachmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dl.mimeType()));
        headers.setContentLength(dl.fileSizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(dl.originalFilename(), StandardCharsets.UTF_8)
                .build());
        headers.add("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(dl.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID attachmentId) {
        exchangeService.deleteAttachment(attachmentId);
    }
}
