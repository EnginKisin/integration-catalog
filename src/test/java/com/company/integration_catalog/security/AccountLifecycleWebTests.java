package com.company.integration_catalog.security;

import com.company.integration_catalog.controller.*;
import com.company.integration_catalog.model.entity.UserAccount;
import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.repository.UserAccountRepository;
import com.company.integration_catalog.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AccountLifecycleController.class, AuthController.class, UserAccountController.class})
@Import({SecurityConfig.class, CredentialAccess.class, AccountDetailsService.class})
class AccountLifecycleWebTests {
    @Autowired MockMvc mvc;
    @MockitoBean AccountLifecycleService lifecycle;
    @MockitoBean UserAccountService userService;
    @MockitoBean UserAccountRepository accounts;

    @Test void resetRequestIsPublicGenericAndRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle);
        for (String email : List.of("known@example.com", "missing@example.com")) {
            mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("If the account is eligible, a reset email will be sent"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"/invitations/accept", "/password-reset/confirm"})
    void tokenPasswordActionsArePublicAndDoNotCreateAuthenticatedSession(String path) throws Exception {
        mvc.perform(post("/api/v1/auth" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "a".repeat(43) + "\",\"newPassword\":\"New-password-123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"short", "            "})
    void weakOrBlankPasswordsNeverReachService(String password) throws Exception {
        mvc.perform(post("/api/v1/auth/invitations/accept").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "a".repeat(43) + "\",\"newPassword\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lifecycle);
    }

    @Test void emailConfirmationIsPublicButEmailChangeRequiresLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/email/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "a".repeat(43) + "\"}"))
                .andExpect(status().isNoContent());
        verify(lifecycle).confirmEmail("a".repeat(43));
        mvc.perform(post("/api/v1/auth/email/change").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"currentPassword\":\"Old-password-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void signedInUserCanChangePasswordAndCurrentSessionIsClosed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/auth/password/change").session(session).with(user("reader").roles("VIEWER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Old-password-123\",\"newPassword\":\"New-password-123\"}"))
                .andExpect(status().isNoContent());
        verify(lifecycle).changePassword("reader", "Old-password-123", "New-password-123");
        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "revoked", "deleted", "unverified"})
    void storedSessionsCannotOutliveAccountAccess(String change) throws Exception {
        UserAccount account = account();
        MockHttpSession session = session(account);
        if (change.equals("disabled")) account.setActive(false);
        if (change.equals("revoked")) account.revokeSessions();
        if (change.equals("unverified")) { account.setEmail("reader@example.com"); account.setEmailVerified(false); }
        when(accounts.findById(1L)).thenReturn(change.equals("deleted") ? Optional.empty() : Optional.of(account));
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void accountDatabaseFailureFailsClosed() throws Exception {
        when(accounts.findById(1L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private-db-detail"));
        mvc.perform(get("/api/v1/auth/me").session(session(account())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Account verification unavailable"));
    }

    @ParameterizedTest @ValueSource(strings = {"/1/active", "/1/role", "/1/permissions"})
    void viewersCannotModifyAccountAccess(String path) throws Exception {
        mvc.perform(put("/api/v1/users" + path).with(user("reader").roles("VIEWER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle, userService);
    }

    @ParameterizedTest @ValueSource(strings = {"/1/invitations/resend", "/1/password-reset", "/1/sessions/revoke"})
    void editorsCannotPerformAdminAccountActions(String path) throws Exception {
        mvc.perform(post("/api/v1/users" + path).with(user("editor").roles("EDITOR")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle);
    }

    @Test void adminAccountActionsCallLifecycleService() throws Exception {
        var account = account();
        when(lifecycle.changeAccess(1L, false, null)).thenReturn(account);
        when(lifecycle.changeAccess(1L, null, UserRole.EDITOR)).thenReturn(account);
        mvc.perform(put("/api/v1/users/1/active").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/1/role").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/1/sessions/revoke").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(lifecycle).revokeSessions(1L);
    }

    @Test void publicSignupIsNotExposed() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test void publicAuthRequestsAreRateLimited() throws Exception {
        for (int i = 0; i < 21; i++) {
            mvc.perform(post("/api/v1/auth/password-reset/request").with(request -> {
                        request.setRemoteAddr("192.0.2.20"); return request;
                    }).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"reader@example.com\"}"))
                    .andExpect(status().is(i < 20 ? 202 : 429));
        }
    }

    @Test void userListIsPagedAndInvalidPageSizeIsRejected() throws Exception {
        when(userService.list(any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(account())));
        mvc.perform(get("/api/v1/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].username").value("reader"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/users?size=101").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private UserAccount account() {
        var account = new UserAccount(); account.setId(1L); account.setUsername("reader");
        account.setPasswordHash("stored-hash"); account.setRole(UserRole.VIEWER); return account;
    }
    private MockHttpSession session(UserAccount account) {
        var principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
