package io.github.davidmasdev.ledgerservice;

import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.account.CreateAccountRequest;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import io.github.davidmasdev.ledgerservice.journal.PostJournalEntryRequest;
import io.github.davidmasdev.ledgerservice.journal.PostingRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Sets up accounts and balances through the services, for tests that are about something else. */
public final class LedgerFixtures {

    private final AccountService accounts;
    private final JournalEntryService journal;

    public LedgerFixtures(AccountService accounts, JournalEntryService journal) {
        this.accounts = accounts;
        this.journal = journal;
    }

    /** An account that may not go negative. */
    public long openAccount(String currency) {
        return open(currency, false);
    }

    /** An account that may go negative, such as the source of external money. */
    public long openFundingAccount(String currency) {
        return open(currency, true);
    }

    /** Moves {@code amount} into the account from a new funding account. Returns the journal entry id. */
    public long fund(long accountId, String currency, String amount) {
        var amountValue = new BigDecimal(amount);
        return journal.post(
                        UUID.randomUUID().toString(),
                        new PostJournalEntryRequest(
                                "funding",
                                List.of(
                                        new PostingRequest(
                                                openFundingAccount(currency), currency, amountValue.negate()),
                                        new PostingRequest(accountId, currency, amountValue))))
                .id();
    }

    public String balanceOf(long accountId) {
        return accounts.get(accountId).balance();
    }

    private long open(String currency, boolean allowNegative) {
        return accounts.open(new CreateAccountRequest("account-" + UUID.randomUUID(), currency, allowNegative))
                .id();
    }
}
