package io.github.davidmasdev.ledgerservice.idempotency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class IdempotencyServiceTests {

    @Autowired
    IdempotencyService idempotency;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void keyWithoutJournalEntryFailsLoudlyInsteadOfReplayingEntryZero() throws Exception {
        var key = UUID.randomUUID().toString();
        var hash = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest("request".getBytes(StandardCharsets.UTF_8)));
        // Unreachable through the service, which stores the key and its entry id in one transaction.
        jdbc.sql("insert into idempotency_keys (key, request_hash) values (?, ?)")
                .params(key, hash)
                .update();

        assertThatThrownBy(
                        () -> transaction.executeWithoutResult(status -> idempotency.execute(key, "request", () -> 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key);
    }
}
