package io.github.davidmasdev.ledgerservice.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.davidmasdev.ledgerservice.shared.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JournalEntryTest {

    @Test
    void acceptsBalancedEntry() {
        var entry = JournalEntry.create("Rent", List.of(line(1, "USD", "-100.00"), line(2, "USD", "100.00")));

        assertThat(entry.getPostings()).hasSize(2);
    }

    @Test
    void acceptsEntryThatBalancesInEachCurrency() {
        var entry = JournalEntry.create(
                "FX trade",
                List.of(
                        line(1, "USD", "-110.00"),
                        line(2, "USD", "110.00"),
                        line(3, "EUR", "100.00"),
                        line(4, "EUR", "-100.00")));

        assertThat(entry.getPostings()).hasSize(4);
    }

    @Test
    void rejectsEntryThatOnlyBalancesAcrossCurrencies() {
        var lines = List.of(line(1, "USD", "10.00"), line(2, "EUR", "-10.00"));

        assertThatThrownBy(() -> JournalEntry.create("Mixed", lines))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("EUR");
    }

    @Test
    void rejectsUnbalancedEntry() {
        var lines = List.of(line(1, "USD", "-100.00"), line(2, "USD", "99.99"));

        assertThatThrownBy(() -> JournalEntry.create("Off by a cent", lines))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("sum to");
    }

    @Test
    void rejectsSinglePosting() {
        var lines = List.of(line(1, "USD", "10.00"));

        assertThatThrownBy(() -> JournalEntry.create("Lonely", lines))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void rejectsZeroAmount() {
        var lines = List.of(line(1, "USD", "0.00"), line(2, "USD", "0"));

        assertThatThrownBy(() -> JournalEntry.create("Nothing", lines))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("zero");
    }

    @ParameterizedTest
    @CsvSource({"JPY, 1.5", "USD, 1.001"})
    void rejectsMoreDecimalsThanTheCurrencyAllows(String currency, String amount) {
        var lines = List.of(line(1, currency, amount), line(2, currency, "-" + amount));

        assertThatThrownBy(() -> JournalEntry.create("Too precise", lines))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void acceptsTrailingZerosBeyondTheCurrencyPrecision() {
        var entry = JournalEntry.create("Padded", List.of(line(1, "JPY", "500.00"), line(2, "JPY", "-500")));

        assertThat(entry.getPostings()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"XYZ", "XXX"})
    void rejectsCodesThatAreNotSpendableCurrencies(String currency) {
        var lines = List.of(line(1, currency, "1"), line(2, currency, "-1"));

        assertThatThrownBy(() -> JournalEntry.create("Funny money", lines))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining(currency);
    }

    @Test
    void reversalNegatesEveryPosting() {
        var original = JournalEntry.create("Rent", List.of(line(1, "USD", "-100.00"), line(2, "USD", "100.00")));

        var reversal = JournalEntry.reversalOf(original);

        assertThat(reversal.getPostings())
                .extracting(Posting::getAccountId, Posting::getAmount)
                .containsExactly(tuple(1L, new BigDecimal("100.00")), tuple(2L, new BigDecimal("-100.00")));
    }

    private static PostingLine line(long accountId, String currency, String amount) {
        return new PostingLine(accountId, currency, new BigDecimal(amount));
    }
}
