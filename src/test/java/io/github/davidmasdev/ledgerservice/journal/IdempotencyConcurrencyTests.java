package io.github.davidmasdev.ledgerservice.journal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.account.CreateAccountRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class IdempotencyConcurrencyTests {

    private static final int REQUESTS = 10;

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Test
    void concurrentRequestsWithSameKeyPostExactlyOnce() throws Exception {
        long a = accounts.open(new CreateAccountRequest("account-" + UUID.randomUUID(), "USD"))
                .id();
        long b = accounts.open(new CreateAccountRequest("account-" + UUID.randomUUID(), "USD"))
                .id();
        var key = UUID.randomUUID().toString();
        var request = new PostJournalEntryRequest(
                "concurrent",
                List.of(
                        new PostingRequest(a, "USD", new BigDecimal("10.00")),
                        new PostingRequest(b, "USD", new BigDecimal("-10.00"))));

        var startGate = new CountDownLatch(1);
        var responses = new ArrayList<JournalEntryResponse>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<JournalEntryResponse>>();
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return journal.post(key, request);
                }));
            }
            startGate.countDown();
            for (var future : futures) {
                responses.add(future.get());
            }
        }

        assertThat(responses).hasSize(REQUESTS).allMatch(responses.getFirst()::equals);
        assertThat(accounts.get(a).balance()).isEqualTo("10.00");
        assertThat(accounts.get(b).balance()).isEqualTo("-10.00");
    }
}
