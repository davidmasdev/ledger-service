package io.github.davidmasdev.ledgerservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.LedgerFixtures;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import io.github.davidmasdev.ledgerservice.transfer.TransferRequest;
import io.github.davidmasdev.ledgerservice.transfer.TransferService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;

/** End to end: a transfer committed in Postgres reaches Kafka through the scheduled relay. */
@IntegrationTest
class OutboxRelayKafkaTests {

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    @Autowired
    TransferService transfers;

    @Autowired
    ConsumerFactory<String, String> consumers;

    @Autowired
    JdbcClient jdbc;

    @Value("${ledger.outbox.topic}")
    String topic;

    @Test
    void transferIsPublishedToKafka() {
        var ledger = new LedgerFixtures(accounts, journal);
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "50.00");

        long entryId = transfers
                .transfer(
                        UUID.randomUUID().toString(),
                        new TransferRequest(from, to, "USD", new BigDecimal("20.00"), null))
                .id();

        var received = new AtomicReference<ConsumerRecord<String, String>>();
        var overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = consumers.createConsumer("test-" + UUID.randomUUID(), null, null, overrides)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.key().equals(String.valueOf(entryId))) {
                        received.set(record);
                    }
                }
                return received.get() != null;
            });
        }

        var record = received.get();
        assertThat(JsonPath.<String>read(record.value(), "$.kind")).isEqualTo("TRANSFER");
        assertThat(JsonPath.<List<String>>read(record.value(), "$.postings[*].amount"))
                .containsExactly("-20.00", "20.00");
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("JournalEntryRecorded");
        var eventId =
                Long.parseLong(new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8));
        await().atMost(Duration.ofSeconds(10)).until(() -> isPublished(eventId));
    }

    private boolean isPublished(long eventId) {
        return jdbc.sql("select published_at is not null from outbox_events where id = ?")
                .param(eventId)
                .query(Boolean.class)
                .single();
    }
}
