package io.github.davidmasdev.ledgerservice.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.LedgerFixtures;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The double-spending test: many concurrent transfers race to spend the same balance. */
@IntegrationTest
class TransferConcurrencyTests {

    private static final int TRANSFERS = 20;

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Autowired
    TransferService transfers;

    @Test
    void concurrentTransfersNeverSpendMoreThanTheBalance() throws Exception {
        var ledger = new LedgerFixtures(accounts, journal);
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "100.00");
        // 20 transfers of 10.00 against a balance of 100.00: exactly half can succeed.
        var request = new TransferRequest(from, to, "USD", new BigDecimal("10.00"), null);

        var startGate = new CountDownLatch(1);
        int succeeded = 0;
        int insufficientFunds = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < TRANSFERS; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return transfers.transfer(UUID.randomUUID().toString(), request);
                }));
            }
            startGate.countDown();
            for (var future : futures) {
                try {
                    future.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause())
                            .isInstanceOf(BusinessRuleViolationException.class)
                            .hasMessageContaining("Insufficient funds");
                    insufficientFunds++;
                }
            }
        }

        assertThat(succeeded).isEqualTo(10);
        assertThat(insufficientFunds).isEqualTo(10);
        assertThat(ledger.balanceOf(from)).isEqualTo("0.00");
        assertThat(ledger.balanceOf(to)).isEqualTo("100.00");
    }
}
