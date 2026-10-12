package io.github.davidmasdev.ledgerservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.LedgerFixtures;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * If Kafka rejects a send, the relay's transaction rolls back and the events stay pending for the next run. The
 * scheduler is off so the test drives the relay itself; this costs a separate application context.
 */
@IntegrationTest
@TestPropertySource(properties = "ledger.outbox.relay.enabled=false")
class OutboxRelayFailureTests {

    @MockitoBean
    KafkaTemplate<String, String> kafka;

    @Autowired
    OutboxRelay relay;

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Autowired
    JdbcClient jdbc;

    @Test
    void failedSendLeavesEventsPendingUntilARetrySucceeds() {
        var ledger = new LedgerFixtures(accounts, journal);
        long entryId = ledger.fund(ledger.openAccount("USD"), "USD", "10.00");

        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unavailable")));
        assertThatThrownBy(relay::publishPending).isInstanceOf(OutboxPublishException.class);
        assertThat(isPublished(entryId)).isFalse();

        when(kafka.send(any(ProducerRecord.class)))
                .thenAnswer(call -> CompletableFuture.completedFuture(new SendResult<>(call.getArgument(0), null)));
        assertThat(relay.publishPending()).isPositive();
        assertThat(isPublished(entryId)).isTrue();
    }

    private boolean isPublished(long entryId) {
        return jdbc.sql("select published_at is not null from outbox_events where aggregate_id = ?")
                .param(entryId)
                .query(Boolean.class)
                .single();
    }
}
