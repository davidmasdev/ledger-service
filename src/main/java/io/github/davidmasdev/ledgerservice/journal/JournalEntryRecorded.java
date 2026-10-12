package io.github.davidmasdev.ledgerservice.journal;

import io.github.davidmasdev.ledgerservice.shared.Money;
import java.time.Instant;
import java.util.List;

/**
 * The event published for every journal entry. This is a contract with consumers, kept separate from
 * {@link JournalEntryResponse} so the API and the event can change independently.
 */
public record JournalEntryRecorded(
        long entryId,
        EntryKind kind,
        String description,
        Long reversalOf,
        Instant createdAt,
        List<RecordedPosting> postings) {

    public static final String TYPE = "JournalEntryRecorded";

    public record RecordedPosting(long accountId, String currency, String amount) {}

    static JournalEntryRecorded from(JournalEntry entry) {
        var postings = entry.getPostings().stream()
                .map(p -> new RecordedPosting(
                        p.getAccountId(), p.getCurrency(), Money.format(p.getAmount(), p.getCurrency())))
                .toList();
        return new JournalEntryRecorded(
                entry.getId(),
                entry.getKind(),
                entry.getDescription(),
                entry.getReversalOf(),
                entry.getCreatedAt(),
                postings);
    }
}
