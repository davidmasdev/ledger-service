package io.github.davidmasdev.ledgerservice.shared;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.davidmasdev.ledgerservice.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The generated API documentation must describe the contract the API actually enforces. */
@IntegrationTest
class OpenApiTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void servesSwaggerUi() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }

    @Test
    void documentsIdempotencyKeyAsRequiredHeader() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.paths['/transfers'].post.parameters[?(@.name == 'Idempotency-Key')].required")
                .asArray()
                .containsExactly(true);
    }

    @Test
    void documentsErrorsAsProblemDetails() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.paths['/transfers'].post.responses['422'].content['application/problem+json']")
                .isNotNull();
    }

    @Test
    void documentsCreatedResponsesWithTheirBody() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath(
                        "$.paths['/transfers'].post.responses['201'].content['application/json'].schema['$ref']")
                .isEqualTo("#/components/schemas/JournalEntryResponse");
    }

    @Test
    void describesTheService() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.info.title")
                .isEqualTo("Ledger Service API");
    }

    @Test
    void documentsAmountsAsDecimalStrings() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.components.schemas.TransferRequest.properties.amount.type")
                .isEqualTo("string");
    }

    @Test
    void documentsOnlyTheResponsesAnOperationCanReturn() {
        // A GET cannot break a ledger rule, so it must not advertise 422.
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.paths['/accounts/{id}'].get.responses")
                .asMap()
                .containsOnlyKeys("200", "404");
    }
}
