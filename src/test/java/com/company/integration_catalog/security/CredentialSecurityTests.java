package com.company.integration_catalog.security;

import com.company.integration_catalog.controller.*;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.*;
import com.company.integration_catalog.service.impl.IntegrationServiceImpl;
import com.company.integration_catalog.validation.IntegrationCompletionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CredentialController.class, IntegrationController.class, UserAccountController.class},
        properties = "catalog.credentials.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({SecurityConfig.class, CredentialAccess.class, CredentialCrypto.class, CredentialService.class,
        IntegrationServiceImpl.class, IntegrationMapper.class, IntegrationCompletionValidator.class,
        UserAccountService.class, AccountDetailsService.class})
class CredentialSecurityTests {
    private static final String REVEAL = "/api/v1/integrations/1/configs/DEV/DESTINATION/credentials";
    @Autowired private MockMvc mvc;
    @Autowired private CredentialCrypto crypto;
    @MockitoBean private UserAccountRepository users;
    @MockitoBean private EnvironmentConfigRepository configs;
    @MockitoBean private IntegrationRepository integrations;
    @MockitoBean private SourceRepository sources;
    @MockitoBean private DestinationRepository destinations;
    @MockitoBean private MiddlewareRepository middlewares;
    @MockitoBean private AccountLifecycleService lifecycle;
    @MockitoBean private AccountAuditService audit;

    @Test
    void anonymousRequestsCannotUseAnyAccountsCredentialPermissions() throws Exception {
        mvc.perform(get(REVEAL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, configs);
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,NONE,403", "EDITOR,NONE,403", "VIEWER,VIEW_CREDENTIALS,200",
            "EDITOR,VIEW_CREDENTIALS,200", "EDITOR,MANAGE_CREDENTIALS,403", "ADMIN,NONE,200"})
    void revealRequiresExplicitPermissionOrAdmin(UserRole role, String permission, int expected) throws Exception {
        UserAccount account = account(role, permission);
        when(users.findByUsername("test")).thenReturn(Optional.of(account));
        if (expected == 200) {
            var config = stored();
            when(configs.findByIntegrationIdAndConfigTargetAndEnvironmentType(1L, ConfigTarget.DESTINATION, EnvironmentType.DEV))
                    .thenReturn(Optional.of(config));
        }
        var result = mvc.perform(get(REVEAL).with(user("test").roles(role.name())))
                .andExpect(status().is(expected));
        if (expected == 200) {
            result.andExpect(jsonPath("$.authCredentials.apiKey").value("private-value"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        } else {
            verifyNoInteractions(configs);
        }
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,MANAGE_CREDENTIALS,403", "EDITOR,NONE,403", "EDITOR,VIEW_CREDENTIALS,403",
            "EDITOR,MANAGE_CREDENTIALS,200", "ADMIN,NONE,200"})
    void credentialsCanOnlyBeReplacedWithManagePermission(UserRole role, String permission, int expected) throws Exception {
        when(users.findByUsername("test")).thenReturn(Optional.of(account(role, permission)));
        if (role != UserRole.VIEWER) {
            var config = stored();
            when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(config.getIntegration()));
            when(configs.findByIntegrationId(1L)).thenReturn(List.of(config));
        }
        mvc.perform(post("/api/v1/integrations/1/configs").with(user("test").roles(role.name())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        [{"environmentType":"DEV","configTarget":"DESTINATION","url":"https://example.com",
                          "authType":"API_KEY","authCredentials":{"apiKey":"replacement"}}]
                        """))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verify(configs, never()).deleteAll(any());
            verify(configs, never()).saveAll(any());
        } else {
            @SuppressWarnings("unchecked")
            var captor = (org.mockito.ArgumentCaptor<List<EnvironmentConfig>>) (org.mockito.ArgumentCaptor<?>)
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(configs).saveAll(captor.capture());
            var saved = captor.getValue().get(0);
            assertThat(saved.getAuthCredentials()).doesNotContainKey("apiKey");
            assertThat(crypto.decrypt(saved.getAuthCredentials(), saved)).containsEntry("apiKey", "replacement");
        }
    }

    @Test
    void editorCanUpdateMetadataWithoutChangingCredentials() throws Exception {
        var old = stored();
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getIntegration()));
        when(configs.findByIntegrationId(1L)).thenReturn(List.of(old));
        mvc.perform(post("/api/v1/integrations/1/configs").with(user("test").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        [{"environmentType":"DEV","configTarget":"DESTINATION","url":"https://new.example.com",
                          "authType":"API_KEY"}]
                        """))
                .andExpect(status().isOk());
        verifyNoInteractions(users);
        verify(configs).saveAll(argThat(rows -> {
            EnvironmentConfig saved = rows.iterator().next();
            return saved.getAuthCredentials().equals(old.getAuthCredentials()) && saved.getId().equals(old.getId());
        }));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"[]", "[{\"environmentType\":\"DEV\",\"configTarget\":\"DESTINATION\",\"url\":\"https://example.com\",\"authType\":\"NONE\"}]"})
    void editorCannotBypassPermissionByDeletingOrClearingConfigs(String body) throws Exception {
        var old = stored();
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(old.getIntegration()));
        when(configs.findByIntegrationId(1L)).thenReturn(List.of(old));
        when(users.findByUsername("test")).thenReturn(Optional.of(account(UserRole.EDITOR, "NONE")));
        mvc.perform(post("/api/v1/integrations/1/configs").with(user("test").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(configs, never()).deleteAll(any());
        verify(configs, never()).saveAll(any());
    }

    @Test
    void permissionRevocationTakesEffectOnTheNextRequest() throws Exception {
        var account = account(UserRole.EDITOR, "VIEW_CREDENTIALS");
        when(users.findByUsername("test")).thenReturn(Optional.of(account));
        when(configs.findByIntegrationIdAndConfigTargetAndEnvironmentType(1L, ConfigTarget.DESTINATION, EnvironmentType.DEV))
                .thenReturn(Optional.of(stored()));
        mvc.perform(get(REVEAL).with(user("test").roles("EDITOR"))).andExpect(status().isOk());
        account.getPermissions().clear();
        mvc.perform(get(REVEAL).with(user("test").roles("EDITOR"))).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanGrantPermissions() throws Exception {
        mvc.perform(put("/api/v1/users/2/permissions").with(user("test").roles("EDITOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"VIEW_CREDENTIALS\"]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void adminCanGrantAndRevokePermissions() throws Exception {
        var account = account(UserRole.EDITOR, "NONE");
        account.setId(2L);
        when(users.findById(2L)).thenReturn(Optional.of(account));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (String body : List.of("{\"permissions\":[\"VIEW_CREDENTIALS\"]}", "{\"permissions\":[]}")) {
            mvc.perform(put("/api/v1/users/2/permissions").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        assertThat(account.getPermissions()).isEmpty();
    }

    private UserAccount account(UserRole role, String permission) {
        UserAccount account = new UserAccount();
        account.setUsername("test");
        account.setRole(role);
        if (!permission.equals("NONE")) account.getPermissions().add(UserPermission.valueOf(permission));
        return account;
    }

    private EnvironmentConfig stored() {
        var config = CredentialCryptoTests.config();
        config.getIntegration().setHasMiddleware(false);
        config.setAuthCredentials(crypto.encrypt(Map.of("apiKey", "private-value"), config));
        return config;
    }
}
