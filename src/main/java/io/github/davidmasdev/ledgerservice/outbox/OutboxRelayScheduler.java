package io.github.davidmasdev.ledgerservice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the relay continuously. Can be switched off with {@code ledger.outbox.relay.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "ledger.outbox.relay.enabled", matchIfMissing = true)
class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;

    OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    // fixedDelay: the next run starts only after this one finishes, so runs never overlap within one instance.
    @Scheduled(fixedDelayString = "${ledger.outbox.relay.poll-interval:500ms}")
    void relay() {
        try {
            // Keep going until nothing is pending, so a backlog drains without waiting for the next tick.
            while (relay.publishPending() > 0) {}
        } catch (OutboxPublishException e) {
            log.warn("Outbox relay failed; pending events will be retried", e);
        }
    }
}
