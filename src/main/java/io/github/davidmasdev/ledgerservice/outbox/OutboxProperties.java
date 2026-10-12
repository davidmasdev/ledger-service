package io.github.davidmasdev.ledgerservice.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param topic Kafka topic the events are published to
 * @param batchSize maximum number of events one relay run publishes (and keeps locked)
 * @param sendTimeout how long one relay run waits for Kafka to acknowledge its sends
 */
@ConfigurationProperties("ledger.outbox")
public record OutboxProperties(
        @DefaultValue("ledger.journal-entries") String topic,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10s") Duration sendTimeout) {}
