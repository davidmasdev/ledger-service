package io.github.davidmasdev.ledgerservice.journal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.account.CreateAccountRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks the guarantees the database enforces on its own, bypassing JPA and the domain model. */
@IntegrationTest
class LedgerSchemaTests {

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Autowired
    JdbcClient jdbc;

    long usdAccount;
    long entryId;

    @BeforeEach
    void postEntry() {
        usdAccount = openAccount("USD");
        long other = openAccount("USD");
        entryId = journal.post(
                        UUID.randomUUID().toString(),
                        new PostJournalEntryRequest(
                                "schema test",
                                List.of(
                                        new PostingRequest(usdAccount, "USD", new BigDecimal("5.00")),
                                        new PostingRequest(other, "USD", new BigDecimal("-5.00")))))
                .id();
    }

    @Test
    void postingsCannotBeUpdated() {
        assertAppendOnly("update postings set amount = amount * 2 where journal_entry_id = ?");
    }

    @Test
    void postingsCannotBeDeleted() {
        assertAppendOnly("delete from postings where journal_entry_id = ?");
    }

    @Test
    void journalEntriesCannotBeUpdated() {
        assertAppendOnly("update journal_entries set description = 'edited' where id = ?");
    }

    @Test
    void journalEntriesCannotBeDeleted() {
        assertAppendOnly("delete from journal_entries where id = ?");
    }

    @Test
    void postingsCannotBeTruncated() {
        assertThatThrownBy(() -> jdbc.sql("truncate postings").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void postingCurrencyMustMatchAccountCurrency() {
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into postings (journal_entry_id, account_id, currency, amount)
                        values (?, ?, 'EUR', 1)""").params(entryId, usdAccount).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertAppendOnly(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).param(entryId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private long openAccount(String currency) {
        return accounts.open(new CreateAccountRequest("account-" + UUID.randomUUID(), currency))
                .id();
    }
}
