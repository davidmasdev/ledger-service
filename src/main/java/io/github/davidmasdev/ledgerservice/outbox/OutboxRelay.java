package io.github.davidmasdev.ledgerservice.outbox;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes pending outbox events to Kafka, at least once.
 *
 * <p>Each run locks a batch of pending events with {@code FOR UPDATE SKIP LOCKED}, so several instances can relay in
 * parallel without picking the same events. It sends them, waits for Kafka to acknowledge every send, and only then
 * marks them published. If any send fails the transaction rolls back and the batch is retried on the next run. A crash
 * between the acknowledgement and the commit also leads to a retry, so consumers must deduplicate on the
 * {@code eventId} header.
 */
@Service
public class OutboxRelay {

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.properties = properties;
    }

    /** Publishes one batch of pending events and returns how many were published. */
    @Transactional
    public int publishPending() {
        var events = jdbc.sql("""
                        select id, aggregate_id, event_type, payload::text as payload
                        from outbox_events
                        where published_at is null
                        order by id
                        limit ?
                        for update skip locked""")
                .param(properties.batchSize())
                .query((rs, rowNum) -> new PendingEvent(
                        rs.getLong("id"),
                        rs.getLong("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("payload")))
                .list();
        if (events.isEmpty()) {
            return 0;
        }

        // Send the whole batch before waiting, so the sends are pipelined rather than one round trip each.
        var sends = new ArrayList<CompletableFuture<?>>();
        for (var event : events) {
            sends.add(kafka.send(toRecord(event)));
        }
        awaitAcknowledgements(sends);

        jdbc.sql("update outbox_events set published_at = now() where id in (:ids)")
                .param("ids", events.stream().map(PendingEvent::id).toList())
                .update();
        return events.size();
    }

    /** Keyed by journal entry id; the outbox id travels as {@code eventId} so consumers can deduplicate. */
    private ProducerRecord<String, String> toRecord(PendingEvent event) {
        var record = new ProducerRecord<>(properties.topic(), String.valueOf(event.aggregateId()), event.payload());
        record.headers().add("eventId", String.valueOf(event.id()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.eventType().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private void awaitAcknowledgements(List<CompletableFuture<?>> sends) {
        try {
            CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Interrupted while publishing outbox events", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxPublishException("Kafka did not acknowledge the outbox batch", e);
        }
    }

    private record PendingEvent(long id, long aggregateId, String eventType, String payload) {}
}
