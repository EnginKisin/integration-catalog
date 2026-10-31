package com.company.integration_catalog.controller;

import com.company.integration_catalog.service.IntegrationService;
import com.company.integration_catalog.service.MappingService;
import com.company.integration_catalog.service.SystemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import com.company.integration_catalog.exception.IntegrationCompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({IntegrationController.class, MappingController.class,
        SystemController.class})
@AutoConfigureMockMvc(addFilters = false)
class RequestValidationTests {
    private static final String BASE = "/api/v1/integrations/1";
    private static final String CONFIG = """
            {"configTarget":"DESTINATION","environmentType":"DEV",
             "url":"https://example.com","authType":"NONE"}
            """;
    private static final String FIELD = """
            {"sourceSchemaId":10,"targetSchemaId":20,"sourceFieldPath":"id",
             "targetFieldPath":"id","isAiGenerated":false}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private IntegrationService integrationService;
    @MockitoBean private MappingService mappingService;
    @MockitoBean private SystemService systemService;

    @Test
    void completionReturnsConflictWithAllMissingRequirements() throws Exception {
        doThrow(new IntegrationCompletionException(List.of("Missing environment", "Missing request schema")))
                .when(integrationService).completeIntegration(1L);
        mvc.perform(patch(BASE + "/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.details[0]").value("Missing environment"))
                .andExpect(jsonPath("$.details[1]").value("Missing request schema"));
    }

    @Test
    void validCompletionReturnsOk() throws Exception {
        mvc.perform(patch(BASE + "/complete")).andExpect(status().isOk());
        verify(integrationService).completeIntegration(1L);
    }

    static Stream<Arguments> missingSystems() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of("System")
                .map(system -> Arguments.of(method, system)));
    }

    @ParameterizedTest
    @MethodSource("missingSystems")
    void returnsNotFoundForMissingSystemReferences(String method, String system) throws Exception {
        String message = system + " not found with id: 30";
        boolean creating = method.equals("POST");
        if (creating) {
            when(integrationService.createIntegration(any())).thenThrow(new EntityNotFoundException(message));
        } else {
            when(integrationService.updateIntegration(eq(1L), any())).thenThrow(new EntityNotFoundException(message));
        }
        ObjectNode body = namedRequest("integrations", "Orders")
                .put("hasMiddleware", true).put("middlewareSystemId", 30L);
        mvc.perform(request(HttpMethod.valueOf(method), creating ? "/api/v1/integrations" : BASE)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(message));
    }

    static Stream<Arguments> invalidMiddlewareSelections() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of(
                Arguments.of(method, true, null),
                Arguments.of(method, false, 30L),
                Arguments.of(method, true, 0L),
                Arguments.of(method, true, -1L)));
    }

    @ParameterizedTest
    @MethodSource("invalidMiddlewareSelections")
    void rejectsInconsistentMiddlewareSelection(String method, boolean hasMiddleware, Long middlewareSystemId)
            throws Exception {
        ObjectNode body = namedRequest("integrations", "Orders").put("hasMiddleware", hasMiddleware);
        if (middlewareSystemId != null) {
            body.put("middlewareSystemId", middlewareSystemId);
        }
        String path = method.equals("POST") ? "/api/v1/integrations" : BASE;
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("middlewareSystemId")));
        verifyNoInteractions(integrationService);
    }

    static Stream<Arguments> validMiddlewareSelections() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of(
                Arguments.of(method, true), Arguments.of(method, false)));
    }

    @ParameterizedTest
    @MethodSource("validMiddlewareSelections")
    void acceptsConsistentMiddlewareSelection(String method, boolean hasMiddleware) throws Exception {
        ObjectNode body = namedRequest("integrations", "Orders").put("hasMiddleware", hasMiddleware);
        if (hasMiddleware) {
            body.put("middlewareSystemId", 30L);
        } else {
            body.putNull("middlewareSystemId");
        }
        boolean creating = method.equals("POST");
        mvc.perform(request(HttpMethod.valueOf(method), creating ? "/api/v1/integrations" : BASE)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(creating ? 201 : 200));
        if (creating) {
            verify(integrationService).createIntegration(argThat(dto ->
                    dto.getHasMiddleware() == hasMiddleware
                            && (hasMiddleware ? Long.valueOf(30L).equals(dto.getMiddlewareSystemId())
                            : dto.getMiddlewareSystemId() == null)));
        } else {
            verify(integrationService).updateIntegration(eq(1L), argThat(dto ->
                    dto.getHasMiddleware() == hasMiddleware
                            && (hasMiddleware ? Long.valueOf(30L).equals(dto.getMiddlewareSystemId())
                            : dto.getMiddlewareSystemId() == null)));
        }
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of("systems", "integrations")
                .flatMap(resource -> Stream.of("", "  \t\n", "x".repeat(101))
                        .map(name -> Arguments.of(resource, name)));
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void rejectsBlankAndOverlongNames(String resource, String name) throws Exception {
        ObjectNode body = namedRequest(resource, name);
        mvc.perform(post("/api/v1/" + resource).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(integrationService, systemService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"systems", "integrations"})
    void acceptsNamesAtTheLengthLimit(String resource) throws Exception {
        mvc.perform(post("/api/v1/" + resource).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(namedRequest(resource, "x".repeat(100)))))
                .andExpect(status().isCreated());
    }

    private ObjectNode namedRequest(String resource, String name) {
        ObjectNode body = objectMapper.createObjectNode().put("name", name).put("description", "Description");
        if (resource.equals("integrations")) {
            body.put("sourceSystemId", 1).put("destinationSystemId", 2)
                    .put("sourcePayloadFormat", "JSON").put("destinationPayloadFormat", "JSON")
                    .put("isSync", false).put("hasMiddleware", false);
        }
        return body;
    }

    static Stream<Arguments> invalidConfigValues() {
        return Stream.of(Arguments.of("url", ""), Arguments.of("url", "   "),
                Arguments.of("url", "https://example.com/" + "x".repeat(81)),
                Arguments.of("timeoutMs", 0), Arguments.of("timeoutMs", -1),
                Arguments.of("retryCount", -1), Arguments.of("retryIntervalMs", -1));
    }

    @ParameterizedTest
    @MethodSource("invalidConfigValues")
    void rejectsInvalidConfigValues(String field, Object value) throws Exception {
        ObjectNode config = (ObjectNode) objectMapper.readTree(CONFIG);
        config.set(field, objectMapper.valueToTree(value));
        mvc.perform(post(BASE + "/configs").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + objectMapper.writeValueAsString(config) + "]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(integrationService);
    }

    @Test
    void acceptsConfigBoundaryValues() throws Exception {
        ObjectNode config = (ObjectNode) objectMapper.readTree(CONFIG);
        String prefix = "https://example.com/";
        config.put("url", prefix + "x".repeat(100 - prefix.length()))
                .put("timeoutMs", 1).put("retryCount", 0).put("retryIntervalMs", 0);
        mvc.perform(post(BASE + "/configs").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + objectMapper.writeValueAsString(config) + "]"))
                .andExpect(status().isOk());
        verify(integrationService).saveEnvironmentConfigs(eq(1L), anyList());
    }

    static Stream<Arguments> invalidFieldPaths() {
        return Stream.of("sourceFieldPath", "targetFieldPath")
                .flatMap(field -> Stream.of("", "  ", "x".repeat(501))
                        .map(value -> Arguments.of(field, value)));
    }

    @ParameterizedTest
    @MethodSource("invalidFieldPaths")
    void rejectsBlankAndOverlongFieldPaths(String field, String value) throws Exception {
        ObjectNode mapping = (ObjectNode) objectMapper.readTree(FIELD);
        mapping.put(field, value);
        mvc.perform(post(BASE + "/mappings/fields").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + objectMapper.writeValueAsString(mapping) + "]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(mappingService);
    }

    @Test
    void acceptsFieldPathsAtTheLengthLimit() throws Exception {
        ObjectNode mapping = (ObjectNode) objectMapper.readTree(FIELD);
        mapping.put("sourceFieldPath", "x".repeat(500)).put("targetFieldPath", "x".repeat(500));
        mvc.perform(post(BASE + "/mappings/fields").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + objectMapper.writeValueAsString(mapping) + "]"))
                .andExpect(status().isOk());
        verify(mappingService).saveFieldMappings(eq(1L), anyList());
    }

    @Test
    void rejectsUpdateWithMissingRequiredFields() throws Exception {
        mvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(integrationService);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"sourceSystemId,0", "sourceSystemId,-1", "destinationSystemId,0", "destinationSystemId,-1"})
    void rejectsNonPositiveSystemIds(String field, long value) throws Exception {
        ObjectNode body = namedRequest("integrations", "Orders").put(field, value);
        mvc.perform(post("/api/v1/integrations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(integrationService);
    }

    @Test
    void acceptsValidUpdate() throws Exception {
        mvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Orders","description":"Order transfer","sourceSystemId":1,"destinationSystemId":2,
                 "sourcePayloadFormat":"JSON","destinationPayloadFormat":"JSON",
                 "isSync":true,"hasMiddleware":false}
                """))
                .andExpect(status().isOk());
        verify(integrationService).updateIntegration(eq(1L), any());
    }

    @Test
    void rejectsSchemaWithMissingRequiredFields() throws Exception {
        mvc.perform(post(BASE + "/mappings/schemas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(mappingService);
    }

    @Test
    void acceptsValidSchema() throws Exception {
        mvc.perform(post(BASE + "/mappings/schemas").contentType(MediaType.APPLICATION_JSON).content("""
                {"schemaType":"SOURCE_TO_DEST_REQUEST","schemaDefinition":{"type":"object"}}
                """))
                .andExpect(status().isOk());
        verify(mappingService).saveSchema(eq(1L), any());
    }

    static Stream<Arguments> invalidLists() {
        return Stream.of(new String[]{"/configs", CONFIG}, new String[]{"/mappings/fields", FIELD})
                .flatMap(pair -> Stream.of("[{}]", "[null]", "[" + pair[1] + ",{}]",
                                "[" + pair[1] + ",null]")
                        .map(body -> Arguments.of(pair[0], body)));
    }

    @ParameterizedTest
    @MethodSource("invalidLists")
    void rejectsInvalidElementsAnywhereInList(String path, String body) throws Exception {
        mvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(integrationService, mappingService);
    }

    static Stream<Arguments> validLists() {
        return Stream.of(Arguments.of("/configs", "[" + CONFIG + "]"),
                Arguments.of("/configs", "[]"),
                Arguments.of("/mappings/fields", "[" + FIELD + "]"),
                Arguments.of("/mappings/fields", "[]"));
    }

    @ParameterizedTest
    @MethodSource("validLists")
    void acceptsValidAndEmptyLists(String path, String body) throws Exception {
        mvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        if (path.equals("/configs")) {
            verify(integrationService).saveEnvironmentConfigs(eq(1L), anyList());
        } else {
            verify(mappingService).saveFieldMappings(eq(1L), anyList());
        }
    }
}
