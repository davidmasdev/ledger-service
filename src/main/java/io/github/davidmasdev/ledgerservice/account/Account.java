package io.github.davidmasdev.ledgerservice.account;

import io.github.davidmasdev.ledgerservice.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {} // for JPA

    public Account(String name, String currency, boolean allowNegative) {
        this.name = name;
        this.currency = Money.currency(currency).getCurrencyCode();
        this.allowNegative = allowNegative;
        // PostgreSQL stores microseconds; truncating keeps the in-memory value equal to the stored one.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
