package io.github.davidmasdev.ledgerservice.transfer;

import io.github.davidmasdev.ledgerservice.idempotency.IdempotencyService;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryResponse;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import io.github.davidmasdev.ledgerservice.journal.PostingLine;
import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A transfer is a two-posting journal entry; this service only translates the request into one. */
@Service
public class TransferService {

    private final JournalEntryService journal;
    private final IdempotencyService idempotency;

    TransferService(JournalEntryService journal, IdempotencyService idempotency) {
        this.journal = journal;
        this.idempotency = idempotency;
    }

    @Transactional
    public JournalEntryResponse transfer(String idempotencyKey, TransferRequest request) {
        long entryId = idempotency.execute(idempotencyKey, fingerprint(request), () -> record(request));
        return journal.get(entryId);
    }

    private long record(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleViolationException("A transfer needs two different accounts");
        }
        var description = request.description() == null || request.description().isBlank()
                ? "Transfer from account %d to account %d".formatted(request.fromAccountId(), request.toAccountId())
                : request.description();
        return journal.record(
                description,
                List.of(
                        new PostingLine(
                                request.fromAccountId(),
                                request.currency(),
                                request.amount().negate()),
                        new PostingLine(request.toAccountId(), request.currency(), request.amount())));
    }

    /** Amounts are normalized, so a retry sending {@code 10} instead of {@code 10.00} is the same request. */
    private static String fingerprint(TransferRequest request) {
        return "POST /transfers "
                + List.of(
                        request.fromAccountId(),
                        request.toAccountId(),
                        request.currency(),
                        request.amount().stripTrailingZeros().toPlainString(),
                        String.valueOf(request.description()));
    }
}
