package io.github.davidmasdev.ledgerservice.journal;

import io.github.davidmasdev.ledgerservice.shared.Money;
import java.time.Instant;
import java.util.List;

public record JournalEntryResponse(
        long id, String description, Long reversalOf, Instant createdAt, List<PostingResponse> postings) {

    public record PostingResponse(long accountId, String currency, String amount) {}

    static JournalEntryResponse from(JournalEntry entry) {
        var postings = entry.getPostings().stream()
                .map(p -> new PostingResponse(
                        p.getAccountId(), p.getCurrency(), Money.format(p.getAmount(), p.getCurrency())))
                .toList();
        return new JournalEntryResponse(
                entry.getId(), entry.getDescription(), entry.getReversalOf(), entry.getCreatedAt(), postings);
    }
}
