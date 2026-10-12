package io.github.davidmasdev.ledgerservice.journal;

import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import io.github.davidmasdev.ledgerservice.shared.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.hibernate.annotations.Immutable;

/**
 * A balanced set of postings. Instances can only be built through {@link #create} and {@link #reversalOf}, which
 * enforce the double-entry rules, and are never modified afterwards ({@code @Immutable}, plus database triggers).
 */
@Entity
@Table(name = "journal_entries")
@Immutable
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "reversal_of")
    private Long reversalOf;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<Posting> postings = new ArrayList<>();

    protected JournalEntry() {} // for JPA

    private JournalEntry(String description, Long reversalOf, List<PostingLine> lines) {
        validate(lines);
        this.description = description;
        this.reversalOf = reversalOf;
        // PostgreSQL stores microseconds; truncating keeps the in-memory value equal to the stored one.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (var line : lines) {
            postings.add(new Posting(this, line));
        }
    }

    public static JournalEntry create(String description, List<PostingLine> lines) {
        return new JournalEntry(description, null, lines);
    }

    /** A new entry that cancels {@code original} by posting every amount with the opposite sign. */
    public static JournalEntry reversalOf(JournalEntry original) {
        var lines = original.postings.stream()
                .map(p -> new PostingLine(
                        p.getAccountId(), p.getCurrency(), p.getAmount().negate()))
                .toList();
        return new JournalEntry("Reversal of journal entry " + original.id, original.id, lines);
    }

    private static void validate(List<PostingLine> lines) {
        if (lines.size() < 2) {
            throw new BusinessRuleViolationException("A journal entry needs at least 2 postings");
        }
        // TreeMap: deterministic order, so the error names the same currency every time.
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (var line : lines) {
            if (line.amount().signum() == 0) {
                throw new BusinessRuleViolationException("Posting amounts must not be zero");
            }
            int digits = Money.currency(line.currency()).getDefaultFractionDigits();
            if (line.amount().stripTrailingZeros().scale() > digits) {
                throw new BusinessRuleViolationException(
                        "%s amounts allow at most %d decimal places".formatted(line.currency(), digits));
            }
            totals.merge(line.currency(), line.amount(), BigDecimal::add);
        }
        totals.forEach((currency, total) -> {
            if (total.signum() != 0) {
                throw new BusinessRuleViolationException(
                        "Postings in %s sum to %s instead of zero".formatted(currency, total.toPlainString()));
            }
        });
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Long getReversalOf() {
        return reversalOf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Posting> getPostings() {
        return Collections.unmodifiableList(postings);
    }
}
