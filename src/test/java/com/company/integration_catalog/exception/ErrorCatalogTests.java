package com.company.integration_catalog.exception;

import com.company.integration_catalog.config.ApiDocumentationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCatalogTests {
    @Test void catalogCoversEveryErrorCodeWithEndpointsAndContextExamples() throws Exception {
        var catalog = new ObjectMapper().readTree(Path.of("docs/error-catalog.json").toFile());
        Set<String> codes = new HashSet<>();
        for (var item : catalog.path("codes")) {
            String code = item.path("code").asText();
            assertThat(codes.add(code)).as(code).isTrue();
            assertThat(item.path("message").asText()).isEqualTo(ErrorCode.valueOf(code).message());
            assertThat(item.path("occurrences").size()).isPositive();
            assertThat(item.path("examples").size()).isEqualTo(item.path("occurrences").size());
            for (var occurrence : item.path("occurrences")) {
                assertThat(occurrence.path("endpoints").size()).isPositive();
                assertThat(occurrence.path("httpStatuses").size()).isPositive();
            }
            for (var example : item.path("examples")) {
                var body = example.path("body");
                switch (example.path("type").asText()) {
                    case "http" -> {
                        assertThat(body.path("code").asText()).isEqualTo(code);
                        assertThat(body.path("error").asText()).isEqualTo(HttpStatus.valueOf(example.path("httpStatus").asInt()).getReasonPhrase());
                        assertThat(body.path("details").isArray()).isTrue();
                        assertThat(body.path("violations").isArray()).isTrue();
                    }
                    case "violation" -> assertThat(body.path("violations").get(0).path("code").asText()).isEqualTo(code);
                    case "job" -> {
                        assertThat(example.path("httpStatus").asInt()).isEqualTo(200);
                        assertThat(body.path("errorCode").asText()).isEqualTo(code);
                    }
                    case "capability" -> {
                        assertThat(example.path("httpStatus").asInt()).isEqualTo(200);
                        assertThat(body.path("code").asText()).isEqualTo(code);
                        assertThat(body.path("available").asBoolean()).isFalse();
                    }
                    default -> throw new AssertionError("Unknown catalog context");
                }
                if (example.path("httpStatus").asInt() == 429)
                    assertThat(example.path("headers").path("Retry-After").asText()).matches("[1-9][0-9]*");
            }
        }
        assertThat(codes).containsExactlyInAnyOrderElementsOf(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
    }

    @Test void openApiPublishesTheSharedSchemaCodesAndRateLimitHeaders() {
        var operation = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("Success")));
        var api = new OpenAPI().paths(new Paths().addPathItem("/api/v1/auth/login", new PathItem().post(operation)));
        new ApiDocumentationConfig().apiErrorResponses().customise(api);
        io.swagger.v3.oas.models.media.Schema<?> schema = api.getComponents().getSchemas().get("ErrorResponse");
        assertThat(schema.getRequired()).contains("code", "message", "details", "violations", "params", "requestId");
        assertThat(schema.getProperties()).containsKeys("code", "message", "details", "violations", "requestId");
        assertThat(schema.getProperties().get("code").getEnum()).hasSize(ErrorCode.values().length);
        assertThat(api.getComponents().getSchemas()).containsKey("Violation");
        io.swagger.v3.oas.models.media.Schema<?> violation = api.getComponents().getSchemas().get("Violation");
        assertThat(violation.getRequired()).containsExactlyInAnyOrder("field", "code", "params");
        assertThat(violation.getProperties().get("field").getNullable()).isTrue();
        assertThat(operation.getResponses()).containsKeys("200", "default", "429");
        assertThat(api.getComponents().getResponses().get("RateLimitError").getHeaders()).containsKeys("Retry-After", "X-Request-ID");
    }
}
