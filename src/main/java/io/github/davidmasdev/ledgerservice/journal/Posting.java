package io.github.davidmasdev.ledgerservice.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** One leg of a {@link JournalEntry}; created only through it. */
@Entity
@Table(name = "postings")
@Immutable
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    // A plain id rather than a relation: the journal never needs to navigate to the account.
    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    protected Posting() {} // for JPA

    Posting(JournalEntry journalEntry, PostingLine line) {
        this.journalEntry = journalEntry;
        this.accountId = line.accountId();
        this.currency = line.currency();
        this.amount = line.amount();
    }

    public long getAccountId() {
        return accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
