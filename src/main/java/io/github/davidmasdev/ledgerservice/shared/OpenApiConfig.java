package io.github.davidmasdev.ledgerservice.shared;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

/** API documentation served at {@code /swagger-ui.html} (UI) and {@code /v3/api-docs} (OpenAPI JSON). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ledger Service API")
                        .version("v1")
                        .description("""
                                Double-entry ledger. Every write that moves money requires an `Idempotency-Key` \
                                header; retrying with the same key returns the original response. Amounts are \
                                decimal strings in the currency's minor units (e.g. `"10.00"`). Errors are RFC 9457 \
                                Problem Details.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }

    /**
     * Every error response is an RFC 9457 Problem Details document (see {@link ApiExceptionHandler}). Controllers only
     * declare which error codes an operation can return; this gives all of them the same body.
     */
    @Bean
    OpenApiCustomizer problemDetailErrors() {
        return openApi -> {
            var problemSchema = ModelConverters.getInstance().readAllAsResolvedSchema(ProblemDetail.class).schema;
            // ProblemDetail's extra properties are written as top-level members, not as a nested object.
            problemSchema.getProperties().remove("properties");
            openApi.getComponents().addSchemas("ProblemDetail", problemSchema);
            var problemContent = new Content()
                    .addMediaType(
                            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")));
            openApi.getPaths()
                    .values()
                    .forEach(path -> path.readOperations()
                            .forEach(operation -> operation.getResponses().forEach((code, response) -> {
                                if (code.startsWith("4") || code.startsWith("5")) {
                                    response.setContent(problemContent);
                                }
                            })));
        };
    }
}
