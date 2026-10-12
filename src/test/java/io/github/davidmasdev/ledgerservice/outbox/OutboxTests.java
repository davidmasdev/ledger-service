package io.github.davidmasdev.ledgerservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.LedgerFixtures;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import io.github.davidmasdev.ledgerservice.journal.PostJournalEntryRequest;
import io.github.davidmasdev.ledgerservice.journal.PostingRequest;
import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import io.github.davidmasdev.ledgerservice.transfer.TransferRequest;
import io.github.davidmasdev.ledgerservice.transfer.TransferService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Every journal entry writes exactly one outbox event, in the same transaction as the entry. */
@IntegrationTest
class OutboxTests {

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Autowired
    TransferService transfers;

    @Autowired
    JdbcClient jdbc;

    LedgerFixtures ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixtures(accounts, journal);
    }

    @Test
    void entryWritesOneEvent() {
        long a = ledger.openAccount("USD");
        long entryId = ledger.fund(a, "USD", "10.00");

        var payload = onlyEventFor(entryId);

        assertThat(JsonPath.<Number>read(payload, "$.entryId").longValue()).isEqualTo(entryId);
        assertThat(JsonPath.<String>read(payload, "$.kind")).isEqualTo("ENTRY");
        assertThat(JsonPath.<List<String>>read(payload, "$.postings[*].amount")).containsExactly("-10.00", "10.00");
    }

    @Test
    void reversalWritesOneEvent() {
        long a = ledger.openAccount("USD");
        long entryId = ledger.fund(a, "USD", "10.00");

        long reversalId = journal.reverse(UUID.randomUUID().toString(), entryId).id();

        var payload = onlyEventFor(reversalId);
        assertThat(JsonPath.<String>read(payload, "$.kind")).isEqualTo("REVERSAL");
        assertThat(JsonPath.<Number>read(payload, "$.reversalOf").longValue()).isEqualTo(entryId);
    }

    @Test
    void transferWritesOneEvent() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "10.00");

        long entryId = transfers
                .transfer(UUID.randomUUID().toString(), transfer(from, to, "4.00"))
                .id();

        assertThat(JsonPath.<String>read(onlyEventFor(entryId), "$.kind")).isEqualTo("TRANSFER");
    }

    @Test
    void rejectedEntryWritesNoEvent() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");

        assertThatThrownBy(() -> transfers.transfer(UUID.randomUUID().toString(), transfer(from, to, "4.00")))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(eventsMentioning(from)).isZero();
    }

    @Test
    void idempotentReplayWritesNoSecondEvent() {
        long a = ledger.openAccount("USD");
        long funding = ledger.openFundingAccount("USD");
        var key = UUID.randomUUID().toString();
        var request = new PostJournalEntryRequest(
                "replayed",
                List.of(
                        new PostingRequest(funding, "USD", new BigDecimal("-3.00")),
                        new PostingRequest(a, "USD", new BigDecimal("3.00"))));

        journal.post(key, request);
        journal.post(key, request);

        assertThat(eventsMentioning(a)).isEqualTo(1);
    }

    private String onlyEventFor(long entryId) {
        return jdbc.sql("select payload::text from outbox_events where aggregate_id = ? and event_type = ?")
                .params(entryId, "JournalEntryRecorded")
                .query(String.class)
                .single();
    }

    private long eventsMentioning(long accountId) {
        return jdbc.sql("select count(*) from outbox_events where payload -> 'postings' @> cast(? as jsonb)")
                .param("[{\"accountId\": %d}]".formatted(accountId))
                .query(Long.class)
                .single();
    }

    private static TransferRequest transfer(long from, long to, String amount) {
        return new TransferRequest(from, to, "USD", new BigDecimal(amount), null);
    }
}
