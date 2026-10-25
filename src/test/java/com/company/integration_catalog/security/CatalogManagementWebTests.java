package com.company.integration_catalog.security;

import com.company.integration_catalog.controller.*;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.IntegrationService;
import com.company.integration_catalog.service.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SourceController.class, DestinationController.class, MiddlewareController.class, IntegrationController.class})
@Import({SecurityConfig.class, CredentialAccess.class, AccountDetailsService.class,
        SourceServiceImpl.class, DestinationServiceImpl.class, MiddlewareServiceImpl.class})
class CatalogManagementWebTests {
    @Autowired MockMvc mvc;
    @MockitoBean SourceRepository sources;
    @MockitoBean DestinationRepository destinations;
    @MockitoBean MiddlewareRepository middlewares;
    @MockitoBean IntegrationRepository integrations;
    @MockitoBean UserAccountRepository users;
    @MockitoBean IntegrationService integrationService;

    static Stream<Arguments> rolesAndOperations() {
        return Stream.of("sources", "destinations", "middlewares").flatMap(type ->
                Stream.of("VIEWER", "EDITOR", "ADMIN").flatMap(role -> Stream.of("GET", "PUT", "PATCH", "DELETE")
                        .map(method -> Arguments.of(type, role, method))));
    }

    @ParameterizedTest @MethodSource("rolesAndOperations")
    void enforcesRolesForAllCatalogTypes(String type, String role, String method) throws Exception {
        boolean allowed = method.equals("GET") || (method.equals("DELETE") ? role.equals("ADMIN") : !role.equals("VIEWER"));
        if (allowed) stub(type, method.equals("GET"), 0);
        String path = "/api/v1/" + type + "/1" + (method.equals("PATCH") ? "/active" : "");
        String body = method.equals("PATCH") ? "{\"active\":false}" : "{\"name\":\"Renamed\",\"description\":\"Updated\"}";
        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("test").roles(role)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(allowed ? (method.equals("DELETE") ? 204 : 200) : 403));
        if (!allowed) verifyNoInteractions(sources, destinations, middlewares, integrations);
    }

    @ParameterizedTest @ValueSource(strings = {"sources", "destinations", "middlewares"})
    void deletionConflictExplainsUsageCount(String type) throws Exception {
        stub(type, false, 5);
        mvc.perform(delete("/api/v1/" + type + "/1").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("5 integration")));
        verify(sources, never()).delete(any()); verify(destinations, never()).delete(any()); verify(middlewares, never()).delete(any());
    }

    @ParameterizedTest @ValueSource(strings = {"sources", "destinations", "middlewares"})
    void existingRecordsCanBeListedTogetherOrFilteredByActiveState(String type) throws Exception {
        switch (type) {
            case "sources" -> when(sources.findAll()).thenReturn(List.of(Source.builder().id(1L).active(true).build(), Source.builder().id(2L).active(false).build()));
            case "destinations" -> when(destinations.findAll()).thenReturn(List.of(Destination.builder().id(1L).active(true).build(), Destination.builder().id(2L).active(false).build()));
            case "middlewares" -> when(middlewares.findAll()).thenReturn(List.of(Middleware.builder().id(1L).active(true).build(), Middleware.builder().id(2L).active(false).build()));
        }
        String path = "/api/v1/" + type;
        mvc.perform(get(path).with(user("test").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(path + "?active=true").with(user("test").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(true)).andExpect(jsonPath("$[0].usageCount").value(0));
        mvc.perform(get(path + "?active=false").with(user("test").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(2));
    }

    @ParameterizedTest @ValueSource(strings = {"sources", "destinations", "middlewares"})
    void updatesValidateBodyAndMissingRecordsReturn404(String type) throws Exception {
        String path = "/api/v1/" + type + "/99";
        mvc.perform(put(path).with(user("test").roles("EDITOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"description\":\"Updated\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path + "/active").with(user("test").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sources, destinations, middlewares);
        mvc.perform(delete(path).with(user("test").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @CsvSource({"VIEWER,403", "EDITOR,200", "ADMIN,200"})
    void reviewConfirmationRequiresEditPermission(String role, int status) throws Exception {
        mvc.perform(post("/api/v1/integrations/1/review/confirm").with(user("test").roles(role)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().is(status));
        if (status == 200) verify(integrationService).confirmIntegrationReview(1L);
        else verifyNoInteractions(integrationService);
    }

    @Test void reviewCannotBeConfirmedWithFalseOrMissingAcknowledgement() throws Exception {
        for (String body : List.of("{}", "{\"confirmed\":false}")) {
            mvc.perform(post("/api/v1/integrations/1/review/confirm").with(user("test").roles("EDITOR")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(integrationService);
    }

    private void stub(String type, boolean readOnly, long usage) {
        switch (type) {
            case "sources" -> {
                var item = Source.builder().id(1L).name("Source").description("Description").build();
                if (readOnly) when(sources.findById(1L)).thenReturn(Optional.of(item));
                else when(sources.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
                lenient().when(sources.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(integrations.countBySourceId(1L)).thenReturn(usage);
            }
            case "destinations" -> {
                var item = Destination.builder().id(1L).name("Destination").description("Description").build();
                if (readOnly) when(destinations.findById(1L)).thenReturn(Optional.of(item));
                else when(destinations.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
                lenient().when(destinations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(integrations.countByDestinationId(1L)).thenReturn(usage);
            }
            case "middlewares" -> {
                var item = Middleware.builder().id(1L).name("Middleware").description("Description").build();
                if (readOnly) when(middlewares.findById(1L)).thenReturn(Optional.of(item));
                else when(middlewares.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
                lenient().when(middlewares.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(integrations.countByMiddlewareId(1L)).thenReturn(usage);
            }
        }
    }
}
