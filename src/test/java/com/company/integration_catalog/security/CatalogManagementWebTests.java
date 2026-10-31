package com.company.integration_catalog.security;

import com.company.integration_catalog.controller.*;
import com.company.integration_catalog.model.entity.CatalogSystem;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.IntegrationService;
import com.company.integration_catalog.service.impl.SystemServiceImpl;
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

@WebMvcTest({SystemController.class, IntegrationController.class})
@Import({SecurityConfig.class, CredentialAccess.class, AccountDetailsService.class, SystemServiceImpl.class})
class CatalogManagementWebTests {
    @Autowired MockMvc mvc;
    @MockitoBean SystemRepository systems;
    @MockitoBean IntegrationRepository integrations;
    @MockitoBean UserAccountRepository users;
    @MockitoBean IntegrationService integrationService;

    static Stream<Arguments> rolesAndOperations() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN").flatMap(role ->
                Stream.of("GET", "POST", "PUT", "PATCH", "DELETE").map(method -> Arguments.of(role, method)));
    }

    @ParameterizedTest @MethodSource("rolesAndOperations")
    void enforcesSystemCatalogRoles(String role, String method) throws Exception {
        boolean allowed = method.equals("GET") || (method.equals("DELETE") ? role.equals("ADMIN") : !role.equals("VIEWER"));
        if (allowed) stub(method.equals("GET"), 0);
        String path = "/api/v1/systems" + (method.equals("POST") ? "" : "/1") + (method.equals("PATCH") ? "/active" : "");
        String body = method.equals("PATCH") ? "{\"active\":false}" : "{\"name\":\"SAP\",\"description\":\"ERP\"}";
        int expected = method.equals("POST") ? 201 : method.equals("DELETE") ? 204 : 200;
        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("test").roles(role)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(allowed ? expected : 403));
        if (!allowed) verifyNoInteractions(systems, integrations);
    }

    @Test void deletionConflictExplainsDistinctIntegrationUsage() throws Exception {
        stub(false, 5);
        mvc.perform(delete("/api/v1/systems/1").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_IN_USE"))
                .andExpect(jsonPath("$.params.usageCount").value(5));
        verify(systems, never()).delete(any());
    }

    @Test void listsAllSystemsOrFiltersByActiveState() throws Exception {
        when(systems.findAll()).thenReturn(List.of(CatalogSystem.builder().id(1L).active(true).build(),
                CatalogSystem.builder().id(2L).active(false).build()));
        mvc.perform(get("/api/v1/systems").with(user("test").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/systems?active=true").with(user("test").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(true)).andExpect(jsonPath("$[0].usageCount").value(0));
        mvc.perform(get("/api/v1/systems?active=false").with(user("test").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(2));
    }

    @Test void validatesBodiesAndReportsMissingSystem() throws Exception {
        mvc.perform(put("/api/v1/systems/99").with(user("test").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"description\":\"ERP\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/systems/99/active").with(user("test").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(systems);
        mvc.perform(delete("/api/v1/systems/99").with(user("test").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"sources", "destinations", "middlewares"})
    void removedCatalogRoutesAreNotAuthorized(String removed) throws Exception {
        mvc.perform(get("/api/v1/" + removed).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(systems, integrations);
    }

    @Test void systemWritesRequireCsrf() throws Exception {
        mvc.perform(post("/api/v1/systems").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"SAP\",\"description\":\"ERP\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(systems);
    }

    @ParameterizedTest @CsvSource({"VIEWER,403", "EDITOR,200", "ADMIN,200"})
    void reviewConfirmationRequiresEditPermission(String role, int status) throws Exception {
        mvc.perform(post("/api/v1/integrations/1/review/confirm").with(user("test").roles(role)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().is(status));
        if (status == 200) verify(integrationService).confirmIntegrationReview(1L);
        else verifyNoInteractions(integrationService);
    }

    @Test void reviewRequiresExplicitAcknowledgement() throws Exception {
        for (String body : List.of("{}", "{\"confirmed\":false}")) {
            mvc.perform(post("/api/v1/integrations/1/review/confirm").with(user("test").roles("EDITOR")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(integrationService);
    }

    private void stub(boolean readOnly, long usage) {
        var item = CatalogSystem.builder().id(1L).name("SAP").description("ERP").build();
        if (readOnly) when(systems.findById(1L)).thenReturn(Optional.of(item));
        else lenient().when(systems.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        lenient().when(systems.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(integrations.countBySystemId(1L)).thenReturn(usage);
    }
}
