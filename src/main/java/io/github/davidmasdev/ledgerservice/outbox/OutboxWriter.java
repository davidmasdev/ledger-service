package io.github.davidmasdev.ledgerservice.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Records an event to be published to Kafka once the surrounding transaction commits. */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    OutboxWriter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // MANDATORY: the event must commit or roll back together with the change it describes.
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(long aggregateId, String eventType, Object payload) {
        jdbc.sql("insert into outbox_events (aggregate_id, event_type, payload) values (?, ?, cast(? as jsonb))")
                .params(aggregateId, eventType, json.writeValueAsString(payload))
                .update();
    }
}
