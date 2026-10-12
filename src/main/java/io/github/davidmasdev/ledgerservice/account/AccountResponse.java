package io.github.davidmasdev.ledgerservice.account;

import io.github.davidmasdev.ledgerservice.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(long id, String name, String currency, String balance, Instant createdAt) {

    static AccountResponse from(Account account, BigDecimal balance) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getCurrency(),
                Money.format(balance, account.getCurrency()),
                account.getCreatedAt());
    }
}
