package io.github.davidmasdev.ledgerservice.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Currency lookups and formatting shared by every feature that handles amounts. */
public final class Money {

    private Money() {}

    /**
     * Resolves an ISO 4217 code. Codes without minor units defined (such as {@code XXX} or {@code XAU}) are rejected
     * because they are not currencies an account can hold.
     */
    public static Currency currency(String code) {
        Currency currency;
        try {
            currency = Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Unknown currency: " + code);
        }
        if (currency.getDefaultFractionDigits() < 0) {
            throw new BusinessRuleViolationException("Not a spendable currency: " + code);
        }
        return currency;
    }

    /**
     * Formats an amount with exactly the currency's minor-unit digits, e.g. {@code 10.0000 USD -> "10.00"}. Amounts go
     * over the wire as strings so clients never parse them into binary floating point.
     */
    public static String format(BigDecimal amount, String currencyCode) {
        int digits = currency(currencyCode).getDefaultFractionDigits();
        // UNNECESSARY: stored amounts never carry more digits than the currency allows, so this must not round.
        return amount.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
    }
}
