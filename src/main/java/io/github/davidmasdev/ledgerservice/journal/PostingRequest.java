package io.github.davidmasdev.ledgerservice.journal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Positive amounts are debits, negative are credits. {@code @Digits} mirrors the {@code numeric(19, 4)} column. */
public record PostingRequest(
        @NotNull Long accountId,

        @NotNull
        @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as USD")
        @Schema(example = "USD")
        String currency,

        // Sent as a string so clients never round-trip money through binary floating point.
        @NotNull @Digits(integer = 15, fraction = 4) @Schema(type = "string", format = "decimal", example = "-10.00")
        BigDecimal amount) {}
