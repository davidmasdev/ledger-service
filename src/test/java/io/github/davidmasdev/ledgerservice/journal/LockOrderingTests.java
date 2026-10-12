package io.github.davidmasdev.ledgerservice.journal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.LedgerFixtures;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Entries that debit the same two protected accounts, listed in opposite orders, run concurrently. Locking in
 * ascending account id order means none of them can deadlock (PostgreSQL would abort one with SQLState 40P01).
 */
@IntegrationTest
class LockOrderingTests {

    private static final int ENTRIES = 20;

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Test
    void entriesDebitingSeveralAccountsInAnyOrderDoNotDeadlock() throws Exception {
        var ledger = new LedgerFixtures(accounts, journal);
        long a = ledger.openAccount("USD");
        long b = ledger.openAccount("USD");
        long sink = ledger.openAccount("USD");
        ledger.fund(a, "USD", "1000.00");
        ledger.fund(b, "USD", "1000.00");

        var startGate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<JournalEntryResponse>>();
            for (int i = 0; i < ENTRIES; i++) {
                var first = i % 2 == 0 ? a : b;
                var second = i % 2 == 0 ? b : a;
                var request = new PostJournalEntryRequest(
                        "debit both",
                        List.of(
                                new PostingRequest(first, "USD", new BigDecimal("-1.00")),
                                new PostingRequest(second, "USD", new BigDecimal("-1.00")),
                                new PostingRequest(sink, "USD", new BigDecimal("2.00"))));
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return journal.post(UUID.randomUUID().toString(), request);
                }));
            }
            startGate.countDown();
            for (var future : futures) {
                future.get(); // throws if any entry failed, e.g. with a deadlock
            }
        }

        assertThat(ledger.balanceOf(a)).isEqualTo("980.00");
        assertThat(ledger.balanceOf(b)).isEqualTo("980.00");
        assertThat(ledger.balanceOf(sink)).isEqualTo("40.00");
    }
}
