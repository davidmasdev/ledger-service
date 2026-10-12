package io.github.davidmasdev.ledgerservice.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.davidmasdev.ledgerservice.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class JournalEntryApiTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void postsEntryAndUpdatesBalances() {
        long cash = openAccount("USD");
        long revenue = openAccount("USD");

        var result = post(newKey(), entry(posting(cash, "USD", "100.00"), posting(revenue, "USD", "-100.00")));

        assertThat(result).hasStatus(HttpStatus.CREATED).containsHeader("Location");
        assertThat(result).bodyJson().extractingPath("$.postings.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("ENTRY");
        assertThat(balanceOf(cash)).isEqualTo("100.00");
        assertThat(balanceOf(revenue)).isEqualTo("-100.00");
    }

    @Test
    void getReturnsTheSameRepresentationAsPost() throws Exception {
        long a = openAccount("JPY");
        long b = openAccount("JPY");
        var posted = post(newKey(), entry(posting(a, "JPY", "500"), posting(b, "JPY", "-500")));

        var fetched = mvc.get().uri(posted.getResponse().getHeader("Location")).exchange();

        assertThat(fetched).hasStatusOk();
        assertThat(fetched.getResponse().getContentAsString())
                .isEqualTo(posted.getResponse().getContentAsString());
    }

    @Test
    void rejectsUnbalancedEntry() {
        long a = openAccount("USD");
        long b = openAccount("USD");

        assertThat(post(newKey(), entry(posting(a, "USD", "100.00"), posting(b, "USD", "-90.00"))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(balanceOf(a)).isEqualTo("0.00");
    }

    @Test
    void rejectsPostingInADifferentCurrencyThanItsAccount() {
        long usd = openAccount("USD");
        long eur = openAccount("EUR");

        assertThat(post(newKey(), entry(posting(usd, "EUR", "10.00"), posting(eur, "EUR", "-10.00"))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("USD");
    }

    @Test
    void rejectsPostingToUnknownAccount() {
        long a = openAccount("USD");

        assertThat(post(newKey(), entry(posting(a, "USD", "10.00"), posting(Long.MAX_VALUE, "USD", "-10.00"))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void rejectsEntryWithFewerThanTwoPostings() {
        long a = openAccount("USD");

        assertThat(post(newKey(), entry(posting(a, "USD", "10.00"))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void requiresIdempotencyKey() {
        long a = openAccount("USD");
        long b = openAccount("USD");

        var result = mvc.post()
                .uri("/journal-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(entry(posting(a, "USD", "1.00"), posting(b, "USD", "-1.00")))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(balanceOf(a)).isEqualTo("0.00");
    }

    @Test
    void replayWithSameKeyReturnsOriginalResponseWithoutPostingAgain() throws Exception {
        long a = openAccount("USD");
        long b = openAccount("USD");
        var key = newKey();
        var body = entry(posting(a, "USD", "25.00"), posting(b, "USD", "-25.00"));

        var first = post(key, body);
        var replay = post(key, body);

        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(replay.getResponse().getHeader("Location"))
                .isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(balanceOf(a)).isEqualTo("25.00");
    }

    @Test
    void replayWithEquivalentAmountsIsTheSameRequest() throws Exception {
        long a = openAccount("USD");
        long b = openAccount("USD");
        var key = newKey();

        var first = post(key, entry(posting(a, "USD", "25.00"), posting(b, "USD", "-25.00")));
        var replay = post(key, entry(posting(a, "USD", "25"), posting(b, "USD", "-25.0")));

        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(balanceOf(a)).isEqualTo("25.00");
    }

    @Test
    void rejectsSameKeyWithDifferentRequest() {
        long a = openAccount("USD");
        long b = openAccount("USD");
        var key = newKey();
        post(key, entry(posting(a, "USD", "25.00"), posting(b, "USD", "-25.00")));

        assertThat(post(key, entry(posting(a, "USD", "99.00"), posting(b, "USD", "-99.00"))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(balanceOf(a)).isEqualTo("25.00");
    }

    @Test
    void failedRequestDoesNotConsumeTheKey() {
        long a = openAccount("USD");
        long b = openAccount("USD");
        var key = newKey();
        post(key, entry(posting(a, "USD", "25.00"), posting(b, "USD", "-20.00")));

        assertThat(post(key, entry(posting(a, "USD", "25.00"), posting(b, "USD", "-25.00"))))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void reversalRestoresBalancesAndCanOnlyHappenOnce() {
        long a = openAccount("USD");
        long b = openAccount("USD");
        long entryId = idOf(post(newKey(), entry(posting(a, "USD", "40.00"), posting(b, "USD", "-40.00"))));

        var reversal = reverse(newKey(), entryId);

        assertThat(reversal).hasStatus(HttpStatus.CREATED);
        assertThat(JsonPath.<String>read(body(reversal), "$.kind")).isEqualTo("REVERSAL");
        assertThat(JsonPath.<Number>read(body(reversal), "$.reversalOf").longValue())
                .isEqualTo(entryId);
        assertThat(balanceOf(a)).isEqualTo("0.00");
        assertThat(balanceOf(b)).isEqualTo("0.00");

        assertThat(reverse(newKey(), entryId))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsEntryThatWouldOverdrawProtectedAccount() {
        long funding = openAccount("USD");
        long protectedAccount = openProtectedAccount("USD");

        assertThat(post(newKey(), entry(posting(protectedAccount, "USD", "-0.01"), posting(funding, "USD", "0.01"))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("Insufficient funds");
        assertThat(balanceOf(protectedAccount)).isEqualTo("0.00");
    }

    @Test
    void protectedAccountCanBeSpentDownToExactlyZero() {
        long funding = openAccount("USD");
        long protectedAccount = openProtectedAccount("USD");
        post(newKey(), entry(posting(funding, "USD", "-50.00"), posting(protectedAccount, "USD", "50.00")));

        assertThat(post(newKey(), entry(posting(protectedAccount, "USD", "-50.00"), posting(funding, "USD", "50.00"))))
                .hasStatus(HttpStatus.CREATED);
        assertThat(balanceOf(protectedAccount)).isEqualTo("0.00");
    }

    @Test
    void accountThatAllowsNegativeBalanceCanGoNegative() {
        long funding = openAccount("USD");
        long protectedAccount = openProtectedAccount("USD");

        assertThat(post(newKey(), entry(posting(funding, "USD", "-75.00"), posting(protectedAccount, "USD", "75.00"))))
                .hasStatus(HttpStatus.CREATED);
        assertThat(balanceOf(funding)).isEqualTo("-75.00");
    }

    @Test
    void reversalThatWouldOverdrawProtectedAccountIsRejected() {
        long funding = openAccount("USD");
        long protectedAccount = openProtectedAccount("USD");
        long other = openProtectedAccount("USD");
        long fundingEntry = idOf(
                post(newKey(), entry(posting(funding, "USD", "-50.00"), posting(protectedAccount, "USD", "50.00"))));
        post(newKey(), entry(posting(protectedAccount, "USD", "-30.00"), posting(other, "USD", "30.00")));

        // Undoing the funding would take 50.00 from an account that only holds 20.00.
        assertThat(reverse(newKey(), fundingEntry)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(balanceOf(protectedAccount)).isEqualTo("20.00");
    }

    @Test
    void returnsNotFoundForUnknownEntry() {
        assertThat(mvc.get().uri("/journal-entries/{id}", Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(reverse(newKey(), Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND);
    }

    /** Most tests here are about entries, not overdrafts, so their accounts may go negative. */
    private long openAccount(String currency) {
        return open(currency, true);
    }

    private long openProtectedAccount(String currency) {
        return open(currency, false);
    }

    private long open(String currency, boolean allowNegative) {
        var result = mvc.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "account-%s", "currency": "%s", "allowNegative": %b}
                        """.formatted(UUID.randomUUID(), currency, allowNegative))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return idOf(result);
    }

    private String balanceOf(long accountId) {
        var result = mvc.get().uri("/accounts/{id}", accountId).exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$.balance");
    }

    private MvcTestResult post(String key, String body) {
        return mvc.post()
                .uri("/journal-entries")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult reverse(String key, long entryId) {
        return mvc.post()
                .uri("/journal-entries/{id}/reversal", entryId)
                .header("Idempotency-Key", key)
                .exchange();
    }

    private static long idOf(MvcTestResult result) {
        return JsonPath.<Number>read(body(result), "$.id").longValue();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String entry(String... postings) {
        return """
                {"description": "test entry", "postings": [%s]}
                """.formatted(String.join(", ", postings));
    }

    private static String posting(long accountId, String currency, String amount) {
        return """
                {"accountId": %d, "currency": "%s", "amount": "%s"}""".formatted(accountId, currency, amount);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
