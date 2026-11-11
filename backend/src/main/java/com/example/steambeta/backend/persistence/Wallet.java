package com.example.steambeta.backend.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(schema = "replica", name = "wallet")
public class Wallet {
    protected Wallet() {}
    @EmbeddedId
    private WalletId id;
    public WalletId getId() { return id; }

    @Column(name = "balance", nullable = false, insertable = false, updatable = false)
    private long balance;

    public long getBalance() { return balance; }

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Instant getUpdatedAt() { return updatedAt; }
}
