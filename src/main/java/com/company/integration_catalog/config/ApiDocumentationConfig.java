package com.company.integration_catalog.config;

import com.company.integration_catalog.exception.ErrorCode;
import com.company.integration_catalog.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class ApiDocumentationConfig {
    @Bean
    public OpenApiCustomizer apiErrorResponses() {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new Components());
            ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(api.getComponents()::addSchemas);
            Schema<?> error = api.getComponents().getSchemas().get("ErrorResponse");
            error.setRequired(List.of("timestamp", "status", "error", "code", "message", "path", "requestId", "details", "violations", "params"));
            error.getProperties().put("requestId", new StringSchema().format("uuid"));
            Schema<?> violation = api.getComponents().getSchemas().get("Violation");
            violation.setRequired(List.of("field", "code", "params"));
            violation.getProperties().get("field").setNullable(true);
            error.getProperties().put("code", new StringSchema()._enum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList()));
            api.getComponents().addResponses("ApiError", errorResponse(false));
            api.getComponents().addResponses("RateLimitError", errorResponse(true));
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/")) return;
                item.readOperationsMap().forEach((method, operation) -> {
                    operation.getResponses().addApiResponse("default", new ApiResponse().$ref("#/components/responses/ApiError"));
                    if (method.name().equals("POST") && (path.startsWith("/api/v1/auth/")
                            || path.endsWith("/invitations/resend") || path.endsWith("/password-reset") || path.endsWith("/ai/jobs"))) {
                        operation.getResponses().addApiResponse("429", new ApiResponse().$ref("#/components/responses/RateLimitError"));
                    }
                });
            });
        };
    }

    private ApiResponse errorResponse(boolean rateLimited) {
        ApiResponse response = new ApiResponse().description(rateLimited
                ? "Rate or queue limit reached. Retry-After is a delay in seconds, not a success guarantee."
                : "Common API error. Localize code and violations[].code; message and details are English fallback explanations.")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))))
                .addHeaderObject("X-Request-ID", new Header().description("Server-generated request identifier")
                        .schema(new StringSchema().format("uuid")));
        if (rateLimited) response.addHeaderObject("Retry-After", new Header().required(true)
                .description("Positive integer delay in seconds")
                .schema(new StringSchema().pattern("^[1-9][0-9]*$").example("60")));
        return response;
    }
}
