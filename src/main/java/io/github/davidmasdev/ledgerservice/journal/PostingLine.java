package io.github.davidmasdev.ledgerservice.journal;

import java.math.BigDecimal;
import java.util.Objects;

/** One leg of a journal entry before it is persisted. Positive amounts are debits, negative are credits. */
public record PostingLine(long accountId, String currency, BigDecimal amount) {

    public PostingLine {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
    }
}
