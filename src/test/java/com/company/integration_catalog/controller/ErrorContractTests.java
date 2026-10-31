package com.company.integration_catalog.controller;

import com.company.integration_catalog.ai.AiMappingDtos;
import com.company.integration_catalog.ai.AiMappingService;
import com.company.integration_catalog.exception.*;
import com.company.integration_catalog.repository.UserAccountRepository;
import com.company.integration_catalog.security.*;
import com.company.integration_catalog.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AccountLifecycleController.class, UserAccountController.class, IntegrationController.class,
        MappingController.class, AiMappingController.class, ApiErrorController.class})
@Import({SecurityConfig.class, CredentialAccess.class, AccountDetailsService.class})
class ErrorContractTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean AccountLifecycleService lifecycle;
    @MockitoBean UserAccountService users;
    @MockitoBean IntegrationService integrations;
    @MockitoBean MappingService mappings;
    @MockitoBean AiMappingService ai;

    @Test void authenticationAndAuthorizationUseDistinctStableCodes() throws Exception {
        contract(mvc.perform(get("/api/v1/integrations")), 401, "AUTH_REQUIRED");
        contract(mvc.perform(post("/api/v1/users").with(user("reader").roles("VIEWER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 403, "ACCESS_DENIED");
        contract(mvc.perform(post("/api/v1/auth/login").with(csrf())
                .param("username", "unknown").param("password", "private-password")), 401, "INVALID_CREDENTIALS");
    }

    @Test void missingAndInvalidCsrfHaveDistinctCodes() throws Exception {
        contract(mvc.perform(post("/api/v1/auth/email/change").with(user("reader"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 403, "CSRF_MISSING");
        contract(mvc.perform(post("/api/v1/auth/email/change").with(user("reader")).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 403, "CSRF_INVALID");
    }

    @Test void returnsAllFormErrorsWithCodesAndSafeConstraintParameters() throws Exception {
        var result = mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"email\":\"not-an-email\",\"role\":\"VIEWER\"}"));
        contract(result, 400, "VALIDATION_FAILED");
        result.andExpect(jsonPath("$.violations[*].field", hasItems("email", "username")))
                .andExpect(jsonPath("$.violations[*].code", hasItems("INVALID_EMAIL", "REQUIRED")));
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("not-an-email");
        var password = mvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + "a".repeat(43) + "\",\"newPassword\":\"short\"}"));
        contract(password, 400, "VALIDATION_FAILED");
        password.andExpect(jsonPath("$.violations[0].field").value("newPassword"))
                .andExpect(jsonPath("$.violations[0].code").value("INVALID_LENGTH"))
                .andExpect(jsonPath("$.violations[0].params.min").value(12))
                .andExpect(jsonPath("$.violations[0].params.max").value(128));
        assertThat(password.andReturn().getResponse().getContentAsString()).doesNotContain("short");
        verifyNoInteractions(users, lifecycle);
    }

    @Test void validatesQueryParametersAndNestedArrayFields() throws Exception {
        var query = mvc.perform(get("/api/v1/users?size=101").with(user("admin").roles("ADMIN")));
        contract(query, 400, "VALIDATION_FAILED");
        query.andExpect(jsonPath("$.violations[0].field").value("size"))
                .andExpect(jsonPath("$.violations[0].code").value("MAX_VALUE"))
                .andExpect(jsonPath("$.violations[0].params.max").value(100));
        var nested = mvc.perform(post("/api/v1/integrations/1/configs").with(user("editor").roles("EDITOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"configTarget\":\"DESTINATION\",\"environmentType\":\"DEV\",\"url\":\"https://example.test\",\"timeoutMs\":0,\"authType\":\"NONE\"}]"));
        contract(nested, 400, "VALIDATION_FAILED");
        nested.andExpect(jsonPath("$.violations[0].field").value("[0].timeoutMs"))
                .andExpect(jsonPath("$.violations[0].code").value("POSITIVE_REQUIRED"));
        verifyNoInteractions(integrations, users);
    }

    @Test void malformedBodyAndParametersDoNotEchoSubmittedContent() throws Exception {
        var body = mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"private-password\","));
        contract(body, 400, "INVALID_REQUEST_BODY");
        assertThat(body.andReturn().getResponse().getContentAsString()).doesNotContain("private-password");
        contract(mvc.perform(get("/api/v1/integrations/not-a-number").with(user("reader").roles("VIEWER"))),
                400, "INVALID_PARAMETER");
        var header = mvc.perform(post("/api/v1/integrations/1/mappings/ai/jobs").with(user("editor").roles("EDITOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        contract(header, 400, "MISSING_REQUEST_HEADER");
        header.andExpect(jsonPath("$.params.header").value("Idempotency-Key"));
    }

    @Test void frameworkErrorsKeepTheCommonEnvelopeAndAllowHeader() throws Exception {
        var method = mvc.perform(get("/api/v1/users/1/active").with(user("admin").roles("ADMIN")));
        contract(method, 405, "METHOD_NOT_ALLOWED");
        method.andExpect(header().string("Allow", containsString("PUT")));
        contract(mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.TEXT_PLAIN).content("private-content")), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test void sanitizesMissingRecordsDatabaseConflictsAndUnexpectedFailures() throws Exception {
        when(integrations.getIntegrationById(1L)).thenThrow(new ResourceNotFoundException("INTEGRATION_NOT_FOUND", "private SQL detail"));
        var missing = mvc.perform(get("/api/v1/integrations/1").with(user("reader").roles("VIEWER")));
        contract(missing, 404, "INTEGRATION_NOT_FOUND");
        assertThat(missing.andReturn().getResponse().getContentAsString()).doesNotContain("private SQL detail");
        doThrow(new DataIntegrityViolationException("SELECT private_password")).when(integrations).getIntegrationById(1L);
        var conflict = mvc.perform(get("/api/v1/integrations/1").with(user("reader").roles("VIEWER")));
        contract(conflict, 409, "DATA_CONFLICT");
        doThrow(new IllegalStateException("private-password stack trace")).when(integrations).getIntegrationById(1L);
        var failure = mvc.perform(get("/api/v1/integrations/1").with(user("reader").roles("VIEWER")));
        contract(failure, 500, "INTERNAL_ERROR");
        assertThat(conflict.andReturn().getResponse().getContentAsString()).doesNotContain("SELECT", "private_password");
        assertThat(failure.andReturn().getResponse().getContentAsString()).doesNotContain("private-password", "stack trace");
    }

    @Test void completionIncludesCodedRulesAndSafeMetadata() throws Exception {
        doThrow(new IntegrationCompletionException(List.of("private raw detail"), List.of(
                new Violation(null, "DESTINATION_CONFIG_REQUIRED", Map.of("environment", "DEV")))))
                .when(integrations).completeIntegration(1L);
        var result = mvc.perform(patch("/api/v1/integrations/1/complete").with(user("editor").roles("EDITOR")).with(csrf()));
        contract(result, 409, "INTEGRATION_INCOMPLETE");
        result.andExpect(jsonPath("$.violations[0].code").value("DESTINATION_CONFIG_REQUIRED"))
                .andExpect(jsonPath("$.violations[0].params.environment").value("DEV"));
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("private raw detail");
    }

    @Test void cooldownAndQueueResponsesProvideRetryAfter() throws Exception {
        doThrow(new ApiException(429, "INVITATION_RATE_LIMITED", "private detail", Map.of(), List.of(), 43L))
                .when(lifecycle).resendInvitation(1L);
        var cooldown = mvc.perform(post("/api/v1/users/1/invitations/resend").with(user("admin").roles("ADMIN")).with(csrf()));
        contract(cooldown, 429, "INVITATION_RATE_LIMITED");
        cooldown.andExpect(header().string("Retry-After", "43"));
        when(ai.create(eq(1L), anyString(), anyString(), any()))
                .thenThrow(new PayloadValidationException(429, "AI_QUEUE_FULL", "queue full"));
        var queue = mvc.perform(post("/api/v1/integrations/1/mappings/ai/jobs").with(user("editor").roles("EDITOR")).with(csrf())
                .header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceSchemaId\":1,\"targetSchemaId\":1,\"expectedContextRevision\":\"v1\",\"protectedMappings\":[]}"));
        contract(queue, 429, "AI_QUEUE_FULL");
        queue.andExpect(header().string("Retry-After", "5"));
    }

    @Test void failedAiJobIsA200StatusRepresentationWithAnErrorCode() throws Exception {
        when(ai.status(1L, "job")).thenReturn(new AiMappingDtos.JobView("job", "FAILED", Instant.now(),
                Instant.now().plusSeconds(3600), "AI_RATE_LIMITED", ErrorCode.message("AI_RATE_LIMITED"), null, null));
        mvc.perform(get("/api/v1/integrations/1/mappings/ai/jobs/job").with(user("reader").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AI_RATE_LIMITED"))
                .andExpect(header().exists("X-Request-ID")).andExpect(header().doesNotExist("Retry-After"));
    }

    @Test void requestIdsAreGeneratedPerRequestAndIgnoreClientSuppliedIds() throws Exception {
        var first = mvc.perform(get("/api/v1/integrations").header("X-Request-ID", "untrusted-client-id"));
        contract(first, 401, "AUTH_REQUIRED");
        var second = mvc.perform(get("/api/v1/integrations"));
        contract(second, 401, "AUTH_REQUIRED");
        assertThat(first.andReturn().getResponse().getHeader("X-Request-ID"))
                .isNotEqualTo("untrusted-client-id").isNotEqualTo(second.andReturn().getResponse().getHeader("X-Request-ID"));
    }

    @Test void servletErrorDispatchUsesTheSameEnvelopeAndOriginalPath() throws Exception {
        var result = mvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/users");
            return request;
        }));
        contract(result, 400, "BAD_REQUEST");
        result.andExpect(jsonPath("$.path").value("/api/v1/users"));
    }

    private void contract(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(httpStatus)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(ErrorCode.message(code)))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.params").isMap()).andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.timestamp", org.hamcrest.Matchers.endsWith("Z"))).andExpect(jsonPath("$.requestId").isString());
        var response = result.andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-ID"));
        assertThat(java.util.UUID.fromString(body.path("requestId").asText()).toString()).isEqualTo(body.path("requestId").asText());
    }
}
