package io.github.davidmasdev.ledgerservice.transfer;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Moves a positive {@code amount} from one account to another. {@code description} is optional. */
public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,

        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as USD")
        String currency,

        @NotNull @Positive @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Size(max = 500) String description) {}
