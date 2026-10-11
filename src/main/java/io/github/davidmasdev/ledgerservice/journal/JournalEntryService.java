package io.github.davidmasdev.ledgerservice.journal;

import static java.util.stream.Collectors.toMap;

import io.github.davidmasdev.ledgerservice.account.Account;
import io.github.davidmasdev.ledgerservice.account.AccountRepository;
import io.github.davidmasdev.ledgerservice.idempotency.IdempotencyService;
import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import io.github.davidmasdev.ledgerservice.shared.ConflictException;
import io.github.davidmasdev.ledgerservice.shared.NotFoundException;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalEntryService {

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;
    private final IdempotencyService idempotency;

    JournalEntryService(JournalEntryRepository entries, AccountRepository accounts, IdempotencyService idempotency) {
        this.entries = entries;
        this.accounts = accounts;
        this.idempotency = idempotency;
    }

    @Transactional
    public JournalEntryResponse post(String idempotencyKey, PostJournalEntryRequest request) {
        // Records have a value-based toString, so equal requests describe themselves identically.
        long entryId = idempotency.execute(idempotencyKey, "POST /journal-entries " + request, () -> create(request));
        return JournalEntryResponse.from(find(entryId));
    }

    @Transactional
    public JournalEntryResponse reverse(String idempotencyKey, long entryId) {
        long reversalId = idempotency.execute(
                idempotencyKey, "POST /journal-entries/%d/reversal".formatted(entryId), () -> createReversal(entryId));
        return JournalEntryResponse.from(find(reversalId));
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(long entryId) {
        return JournalEntryResponse.from(find(entryId));
    }

    private long create(PostJournalEntryRequest request) {
        var lines = request.postings().stream()
                .map(p -> new PostingLine(p.accountId(), p.currency(), p.amount()))
                .toList();
        var entry = JournalEntry.create(request.description(), lines);
        checkAccounts(lines);
        return entries.save(entry).getId();
    }

    private long createReversal(long entryId) {
        var original = find(entryId);
        // Friendly error for the common case; the unique constraint on reversal_of still catches concurrent reversals.
        if (entries.existsByReversalOf(entryId)) {
            throw new ConflictException("Journal entry %d has already been reversed".formatted(entryId));
        }
        return entries.save(JournalEntry.reversalOf(original)).getId();
    }

    /** Every posting must target an existing account in that account's currency. */
    private void checkAccounts(List<PostingLine> lines) {
        var ids = lines.stream().map(PostingLine::accountId).distinct().toList();
        var byId = accounts.findAllById(ids).stream().collect(toMap(Account::getId, Function.identity()));
        for (var line : lines) {
            var account = byId.get(line.accountId());
            if (account == null) {
                throw new BusinessRuleViolationException("Account %d does not exist".formatted(line.accountId()));
            }
            if (!account.getCurrency().equals(line.currency())) {
                throw new BusinessRuleViolationException("Account %d holds %s, so it cannot take a posting in %s"
                        .formatted(line.accountId(), account.getCurrency(), line.currency()));
            }
        }
    }

    private JournalEntry find(long entryId) {
        return entries.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Journal entry %d not found".formatted(entryId)));
    }
}
