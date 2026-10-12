package io.github.davidmasdev.ledgerservice.outbox;

/** Kafka did not acknowledge a batch; the relay's transaction rolls back so the events stay pending. */
public class OutboxPublishException extends RuntimeException {

    OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
