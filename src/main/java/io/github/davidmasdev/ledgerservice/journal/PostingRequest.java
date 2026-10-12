package io.github.davidmasdev.ledgerservice.journal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Positive amounts are debits, negative are credits. {@code @Digits} mirrors the {@code numeric(19, 4)} column. */
public record PostingRequest(
        @NotNull Long accountId,

        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as USD")
        String currency,

        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount) {}
