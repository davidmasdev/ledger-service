package io.github.davidmasdev.ledgerservice.journal;

import static java.util.stream.Collectors.toMap;

import io.github.davidmasdev.ledgerservice.account.Account;
import io.github.davidmasdev.ledgerservice.account.AccountRepository;
import io.github.davidmasdev.ledgerservice.idempotency.IdempotencyService;
import io.github.davidmasdev.ledgerservice.outbox.OutboxWriter;
import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import io.github.davidmasdev.ledgerservice.shared.ConflictException;
import io.github.davidmasdev.ledgerservice.shared.NotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalEntryService {

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;
    private final IdempotencyService idempotency;
    private final OutboxWriter outbox;

    JournalEntryService(
            JournalEntryRepository entries,
            AccountRepository accounts,
            IdempotencyService idempotency,
            OutboxWriter outbox) {
        this.entries = entries;
        this.accounts = accounts;
        this.idempotency = idempotency;
        this.outbox = outbox;
    }

    @Transactional
    public JournalEntryResponse post(String idempotencyKey, PostJournalEntryRequest request) {
        var lines = request.postings().stream()
                .map(p -> new PostingLine(p.accountId(), p.currency(), p.amount()))
                .toList();
        long entryId = idempotency.execute(
                idempotencyKey, fingerprint(request), () -> record(EntryKind.ENTRY, request.description(), lines));
        return JournalEntryResponse.from(find(entryId));
    }

    /**
     * Describes the request so that equivalent requests describe themselves identically: amounts are normalized, so a
     * retry sending {@code 10} instead of {@code 10.00} is recognized as the same request.
     */
    private static String fingerprint(PostJournalEntryRequest request) {
        var postings = request.postings().stream()
                .map(p -> "%d:%s:%s"
                        .formatted(
                                p.accountId(),
                                p.currency(),
                                p.amount().stripTrailingZeros().toPlainString()))
                .toList();
        return "POST /journal-entries " + List.of(request.description(), postings);
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

    /**
     * Records a new entry in the caller's transaction, for features built on journal entries such as transfers. The
     * caller is responsible for idempotency.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long record(EntryKind kind, String description, List<PostingLine> lines) {
        return save(JournalEntry.create(kind, description, lines));
    }

    private long createReversal(long entryId) {
        var original = find(entryId);
        // Friendly error for the common case; the unique constraint on reversal_of still catches concurrent reversals.
        if (entries.existsByReversalOf(entryId)) {
            throw new ConflictException("Journal entry %d has already been reversed".formatted(entryId));
        }
        return save(JournalEntry.reversalOf(original));
    }

    /**
     * The single write path: every entry is checked against its accounts and their balances, saved, and announced
     * through the outbox in the same transaction, so the event exists if and only if the entry does.
     */
    private long save(JournalEntry entry) {
        var accountsById = checkAccounts(entry.getPostings());
        checkBalances(entry.getPostings(), accountsById);
        var saved = entries.save(entry);
        outbox.append(saved.getId(), JournalEntryRecorded.TYPE, JournalEntryRecorded.from(saved));
        return saved.getId();
    }

    /** Every posting must target an existing account in that account's currency. */
    private Map<Long, Account> checkAccounts(List<Posting> postings) {
        var ids = postings.stream().map(Posting::getAccountId).distinct().toList();
        var byId = accounts.findAllById(ids).stream().collect(toMap(Account::getId, Function.identity()));
        for (var posting : postings) {
            var account = byId.get(posting.getAccountId());
            if (account == null) {
                throw new BusinessRuleViolationException("Account %d does not exist".formatted(posting.getAccountId()));
            }
            if (!account.getCurrency().equals(posting.getCurrency())) {
                throw new BusinessRuleViolationException("Account %d holds %s, so it cannot take a posting in %s"
                        .formatted(posting.getAccountId(), account.getCurrency(), posting.getCurrency()));
            }
        }
        return byId;
    }

    /**
     * Accounts that may not go negative and lose money in this entry are locked, then checked. The lock is held until
     * commit, so concurrent debits of the same account run one after another, and each one's balance query (a new
     * statement under READ COMMITTED) sees the postings of those before it. Accounts that only receive money are not
     * locked: a concurrent credit can only make the check more conservative.
     */
    private void checkBalances(List<Posting> postings, Map<Long, Account> accountsById) {
        Map<Long, BigDecimal> netChange = new HashMap<>();
        for (var posting : postings) {
            netChange.merge(posting.getAccountId(), posting.getAmount(), BigDecimal::add);
        }
        var debitedProtectedAccounts = netChange.entrySet().stream()
                .filter(change -> change.getValue().signum() < 0)
                .filter(change -> !accountsById.get(change.getKey()).isAllowNegative())
                .map(Map.Entry::getKey)
                .toList();
        if (debitedProtectedAccounts.isEmpty()) {
            return;
        }
        for (var account : accounts.lockForUpdate(debitedProtectedAccounts)) {
            var balanceAfter = accounts.balanceOf(account.getId()).add(netChange.get(account.getId()));
            if (balanceAfter.signum() < 0) {
                throw new BusinessRuleViolationException("Insufficient funds in account %d".formatted(account.getId()));
            }
        }
    }

    private JournalEntry find(long entryId) {
        return entries.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Journal entry %d not found".formatted(entryId)));
    }
}
