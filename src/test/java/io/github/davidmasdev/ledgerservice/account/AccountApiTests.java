package io.github.davidmasdev.ledgerservice.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AccountApiTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void opensAccountWithZeroBalance() {
        var result = open(uniqueName(), "USD");

        assertThat(result).hasStatus(HttpStatus.CREATED).containsHeader("Location");
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("USD");
        assertThat(result).bodyJson().extractingPath("$.balance").isEqualTo("0.00");

        var location = result.getResponse().getHeader("Location");
        assertThat(mvc.get().uri(location))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.balance")
                .isEqualTo("0.00");
    }

    @Test
    void rejectsMalformedCurrencyCode() {
        assertThat(open(uniqueName(), "usd"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThat(open(uniqueName(), "XYZ"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsDuplicateName() {
        var name = uniqueName();
        assertThat(open(name, "USD")).hasStatus(HttpStatus.CREATED);

        assertThat(open(name, "EUR")).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void returnsNotFoundForUnknownAccount() {
        assertThat(mvc.get().uri("/accounts/{id}", Long.MAX_VALUE))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MvcTestResult open(String name, String currency) {
        return mvc.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "currency": "%s"}
                        """.formatted(name, currency))
                .exchange();
    }

    private static String uniqueName() {
        return "account-" + UUID.randomUUID();
    }
}
