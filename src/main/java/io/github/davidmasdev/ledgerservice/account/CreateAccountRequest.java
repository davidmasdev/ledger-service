package io.github.davidmasdev.ledgerservice.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code allowNegative} is optional; accounts may not go negative unless it is {@code true}. */
public record CreateAccountRequest(
        @NotBlank @Size(max = 200) String name,

        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as USD")
        String currency,

        Boolean allowNegative) {}
