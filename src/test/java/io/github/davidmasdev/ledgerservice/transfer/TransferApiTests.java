package io.github.davidmasdev.ledgerservice.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import io.github.davidmasdev.ledgerservice.LedgerFixtures;
import io.github.davidmasdev.ledgerservice.account.AccountService;
import io.github.davidmasdev.ledgerservice.journal.JournalEntryService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class TransferApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    JournalEntryService journal;

    LedgerFixtures ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixtures(accounts, journal);
    }

    @Test
    void transfersMoneyBetweenAccounts() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "100.00");

        var result = transfer(newKey(), from, to, "USD", "30.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Location")).matches(".*/journal-entries/\\d+");
        assertThat(result).bodyJson().extractingPath("$.postings.length()").isEqualTo(2);
        assertThat(ledger.balanceOf(from)).isEqualTo("70.00");
        assertThat(ledger.balanceOf(to)).isEqualTo("30.00");
    }

    @Test
    void rejectsTransferBeyondTheBalance() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "10.00");

        assertThat(transfer(newKey(), from, to, "USD", "10.01"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("Insufficient funds");
        assertThat(ledger.balanceOf(from)).isEqualTo("10.00");
        assertThat(ledger.balanceOf(to)).isEqualTo("0.00");
    }

    @Test
    void rejectsTransferToTheSameAccount() {
        long account = ledger.openAccount("USD");
        ledger.fund(account, "USD", "10.00");

        assertThat(transfer(newKey(), account, account, "USD", "5.00")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00"})
    void rejectsAmountsThatAreNotPositive(String amount) {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");

        assertThat(transfer(newKey(), from, to, "USD", amount)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsTransferInADifferentCurrencyThanAnAccount() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("EUR");
        ledger.fund(from, "USD", "10.00");

        assertThat(transfer(newKey(), from, to, "USD", "5.00")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ledger.balanceOf(from)).isEqualTo("10.00");
    }

    @Test
    void requiresIdempotencyKey() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");

        var result = mvc.post()
                .uri("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(from, to, "USD", "1.00"))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void replayReturnsTheOriginalResponseWithoutMovingMoneyAgain() throws Exception {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "100.00");
        var key = newKey();

        var first = transfer(key, from, to, "USD", "40.00");
        var replay = transfer(key, from, to, "USD", "40");

        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(ledger.balanceOf(from)).isEqualTo("60.00");
    }

    @Test
    void rejectsSameKeyForADifferentTransfer() {
        long from = ledger.openAccount("USD");
        long to = ledger.openAccount("USD");
        ledger.fund(from, "USD", "100.00");
        var key = newKey();
        transfer(key, from, to, "USD", "40.00");

        assertThat(transfer(key, from, to, "USD", "41.00")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ledger.balanceOf(from)).isEqualTo("60.00");
    }

    private MvcTestResult transfer(String key, long from, long to, String currency, String amount) {
        return mvc.post()
                .uri("/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(from, to, currency, amount))
                .exchange();
    }

    private static String body(long from, long to, String currency, String amount) {
        return """
                {"fromAccountId": %d, "toAccountId": %d, "currency": "%s", "amount": "%s"}
                """.formatted(from, to, currency, amount);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
