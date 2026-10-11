package io.github.davidmasdev.ledgerservice.idempotency;

import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongSupplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a money-moving operation run at most once per {@code Idempotency-Key}.
 *
 * <p>The key row is inserted first, in the caller's transaction. If another request with the same key is in flight,
 * PostgreSQL blocks this insert on the primary key until that transaction ends: on commit we take the replay path, on
 * rollback our insert goes through and we run the operation ourselves. Failed requests therefore never consume a key.
 */
@Service
public class IdempotencyService {

    private final JdbcClient jdbc;

    IdempotencyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Runs {@code operation} unless {@code key} was already used, in which case returns the journal entry id the first
     * run produced.
     *
     * @param request a description of the operation and its input; a key reused with a different request is rejected
     * @param operation creates the journal entry and returns its id
     */
    // MANDATORY: the key and the operation's writes must commit or roll back together.
    @Transactional(propagation = Propagation.MANDATORY)
    public long execute(String key, String request, LongSupplier operation) {
        var requestHash = sha256(request);
        int inserted = jdbc.sql("""
                        insert into idempotency_keys (key, request_hash) values (?, ?)
                        on conflict (key) do nothing""").params(key, requestHash).update();

        if (inserted == 1) {
            long journalEntryId = operation.getAsLong();
            jdbc.sql("update idempotency_keys set journal_entry_id = ? where key = ?")
                    .params(journalEntryId, key)
                    .update();
            return journalEntryId;
        }

        var stored = jdbc.sql("select request_hash, journal_entry_id from idempotency_keys where key = ?")
                .param(key)
                .query((rs, rowNum) -> new StoredKey(rs.getString("request_hash"), rs.getLong("journal_entry_id")))
                .single();
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleViolationException(
                    "Idempotency-Key '%s' was already used for a different request".formatted(key));
        }
        return stored.journalEntryId();
    }

    private record StoredKey(String requestHash, long journalEntryId) {}

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM must support SHA-256", e);
        }
    }
}
