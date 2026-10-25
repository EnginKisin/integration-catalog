package com.company.integration_catalog.security;

import com.company.integration_catalog.controller.*;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.UserAccountRepository;
import com.company.integration_catalog.service.IntegrationService;
import com.company.integration_catalog.service.UserAccountService;
import com.company.integration_catalog.service.AccountLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({IntegrationController.class, AuthController.class, UserAccountController.class})
@Import({SecurityConfig.class, AccountDetailsService.class, UserAccountService.class, CredentialAccess.class})
class SecurityTests {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private UserAccountRepository accounts;
    @MockitoBean private IntegrationService integrations;
    @MockitoBean private AccountLifecycleService lifecycle;
    @MockitoBean private com.company.integration_catalog.service.AccountAuditService audit;

    @Test
    void anonymousReadsReturnJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/integrations"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(integrations);
    }

    @ParameterizedTest
    @CsvSource({
            "VIEWER,GET,/api/v1/integrations,200", "EDITOR,GET,/api/v1/integrations,200",
            "ADMIN,GET,/api/v1/integrations,200",
            "VIEWER,POST,/api/v1/integrations/1/configs,403",
            "EDITOR,POST,/api/v1/integrations/1/configs,200",
            "ADMIN,POST,/api/v1/integrations/1/configs,200",
            "VIEWER,PATCH,/api/v1/integrations/1/complete,403",
            "EDITOR,PATCH,/api/v1/integrations/1/complete,200",
            "ADMIN,PATCH,/api/v1/integrations/1/complete,200",
            "VIEWER,DELETE,/api/v1/integrations/1,403",
            "EDITOR,DELETE,/api/v1/integrations/1,403",
            "ADMIN,DELETE,/api/v1/integrations/1,204",
            "VIEWER,GET,/api/v1/users,403", "EDITOR,GET,/api/v1/users,403", "ADMIN,GET,/api/v1/users,200"
    })
    void enforcesRoleBoundaries(String role, String method, String path, int expected) throws Exception {
        if (role.equals("ADMIN") && path.equals("/api/v1/users")) {
            when(accounts.findAll(any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(org.springframework.data.domain.Page.empty());
        }
        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("test").roles(role)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verifyNoInteractions(integrations, accounts);
        }
    }

    @Test
    void writesRequireCsrfEvenForAdmins() throws Exception {
        mvc.perform(delete("/api/v1/integrations/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(integrations);
    }

    @Test
    void unknownEndpointsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/unknown").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void realLoginPersistsSessionAndLogoutInvalidatesIt() throws Exception {
        UserAccount account = account();
        when(accounts.findByUsername("reader")).thenReturn(Optional.of(account));
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        var tokenResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        String oldSessionId = session.getId();
        var token = mapper.readTree(tokenResult.getResponse().getContentAsString());

        mvc.perform(post("/api/v1/auth/login").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .param("username", "reader").param("password", "Test-password-123"))
                .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("reader"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_VIEWER"));
        mvc.perform(get("/api/v1/integrations").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void badLoginDoesNotRevealWhetherUsernameExists() throws Exception {
        when(accounts.findByUsername("reader")).thenReturn(Optional.of(account()));
        for (String username : List.of("reader", "missing")) {
            mvc.perform(post("/api/v1/auth/login").with(csrf())
                            .param("username", username).param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void inactiveOrUnverifiedUsersCannotLogin(boolean inactive) throws Exception {
        UserAccount account = account();
        if (inactive) account.setActive(false);
        else { account.setEmail("reader@example.com"); account.setEmailVerified(false); }
        when(accounts.findByUsername("reader")).thenReturn(Optional.of(account));
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .param("username", "reader").param("password", "Test-password-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void adminInvitesUserWithoutChoosingTheirPassword() throws Exception {
        UserAccount invited = account();
        invited.setEmail("reader@example.com"); invited.setActive(false);
        when(lifecycle.invite("reader", "reader@example.com", UserRole.VIEWER)).thenReturn(invited);
        mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"reader","email":"reader@example.com","role":"VIEWER"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void editorCannotCreateAccounts() throws Exception {
        mvc.perform(post("/api/v1/users").with(user("editor").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    @Test
    void duplicateUsernameReturnsConflict() throws Exception {
        when(lifecycle.invite("reader", "reader@example.com", UserRole.VIEWER))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT));
        mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"reader","email":"reader@example.com","role":"VIEWER"}
                        """))
                .andExpect(status().isConflict());
        verify(accounts, never()).save(any());
    }

    @Test
    void passwordBasedAccountCreationWithoutEmailIsRejected() throws Exception {
        mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"reader","password":"short","role":"VIEWER"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accounts);
    }

    @Test
    void readResponsesNeverExposeIntegrationCredentials() throws Exception {
        Integration integration = Integration.builder().id(1L).build();
        integration.getEnvironmentConfigs().add(EnvironmentConfig.builder()
                .authType(AuthType.API_KEY).authCredentials(Map.of("apiKey", "private-value")).build());
        when(integrations.getIntegrationById(1L)).thenReturn(new IntegrationMapper().toResponse(integration));
        mvc.perform(get("/api/v1/integrations/1").with(user("reader").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environmentConfigs[0].authCredentials").doesNotExist())
                .andExpect(jsonPath("$.environmentConfigs[0].credentialsConfigured").value(true));
    }

    private UserAccount account() {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setUsername("reader");
        account.setPasswordHash(encoder.encode("Test-password-123"));
        account.setRole(UserRole.VIEWER);
        return account;
    }
}
