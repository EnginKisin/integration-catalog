package com.company.integration_catalog.service;

import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.security.SecurityConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleTests {
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final String RAW = "a".repeat(43);
    @Mock UserAccountRepository accounts;
    @Mock AccountTokenRepository tokens;
    @Mock AccountMailService mail;
    @Mock AccountAuditService audit;
    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
    private AccountLifecycleService service;

    @BeforeEach void setUp() {
        service = new AccountLifecycleService(accounts, tokens, encoder, mail, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void invitationHasNoUsablePasswordAndOnlyStoresTokenHash() throws Exception {
        when(accounts.save(any())).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0); account.setId(1L); return account;
        });
        UserAccount account = service.invite("reader", "Reader@Example.com", UserRole.VIEWER);
        assertThat(account.isActive()).isFalse(); assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.loginAllowed()).isFalse(); assertThat(account.getEmail()).isEqualTo("reader@example.com");
        var token = ArgumentCaptor.forClass(AccountToken.class);
        var raw = ArgumentCaptor.forClass(String.class);
        verify(tokens).save(token.capture());
        verify(mail).action(eq("reader@example.com"), eq("INVITATION"), raw.capture());
        assertThat(raw.getValue()).hasSize(43);
        assertThat(token.getValue().getTokenHash()).isEqualTo(hash(raw.getValue())).isNotEqualTo(raw.getValue());
        assertThat(token.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        verify(audit).record(1L, "INVITED");
    }

    @Test void duplicateEmailDoesNotCreateAnotherAccount() {
        when(accounts.findByEmail("reader@example.com")).thenReturn(Optional.of(account()));
        assertThatThrownBy(() -> service.invite("new", "reader@example.com", UserRole.EDITOR))
                .isInstanceOf(ResponseStatusException.class);
        verify(accounts, never()).save(any()); verifyNoInteractions(mail, tokens);
    }

    @Test void invitationAcceptanceVerifiesEmailSetsPasswordAndConsumesLink() throws Exception {
        var account = account(); account.setActive(false); account.setEmailVerified(false);
        var token = token(account, AccountTokenPurpose.INVITATION);
        stubToken(token);
        service.acceptInvitation(RAW, "New-password-123");
        assertThat(account.loginAllowed()).isTrue();
        assertThat(encoder.matches("New-password-123", account.getPasswordHash())).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(NOW); assertThat(account.getSessionVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.acceptInvitation(RAW, "Another-password-123"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"expired", "used", "wrong-purpose", "old-session"})
    void invalidLinksCannotChangePassword(String problem) throws Exception {
        var account = account();
        var token = token(account, AccountTokenPurpose.PASSWORD_RESET);
        switch (problem) {
            case "expired" -> token.setExpiresAt(NOW);
            case "used" -> token.setUsedAt(NOW.minusSeconds(1));
            case "wrong-purpose" -> token.setPurpose(AccountTokenPurpose.EMAIL_CHANGE);
            case "old-session" -> account.revokeSessions();
        }
        String oldHash = account.getPasswordHash();
        stubToken(token);
        assertThatThrownBy(() -> service.resetPassword(RAW, "New-password-123"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(account.getPasswordHash()).isEqualTo(oldHash);
        verifyNoInteractions(mail, audit);
    }

    @Test void passwordResetChangesHashAndRevokesSessions() throws Exception {
        var account = account(); var token = token(account, AccountTokenPurpose.PASSWORD_RESET); stubToken(token);
        service.resetPassword(RAW, "New-password-123");
        assertThat(encoder.matches("New-password-123", account.getPasswordHash())).isTrue();
        assertThat(account.getSessionVersion()).isEqualTo(1);
        verify(mail).notice(account.getEmail(), "Password reset; all sessions signed out");
    }

    @Test void publicResetQueuesIdenticalWorkWithoutLookingUpAccounts() {
        service.requestPasswordReset("Known@Example.com"); service.requestPasswordReset("Missing@Example.com");
        verify(mail).requestPasswordReset("known@example.com"); verify(mail).requestPasswordReset("missing@example.com");
        verifyNoInteractions(accounts, tokens);
    }

    @Test void unknownResetRequestsDoNotSendMail() {
        service.processPasswordResetRequest("missing@example.com");
        verifyNoInteractions(mail, tokens);
    }

    @ParameterizedTest @ValueSource(strings = {"inactive", "unverified", "recent"})
    void resetWorkerSkipsIneligibleOrThrottledAccounts(String state) {
        var account = account();
        when(accounts.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        if (state.equals("inactive")) account.setActive(false);
        if (state.equals("unverified")) account.setEmailVerified(false);
        if (state.equals("recent")) when(tokens.findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(1L, AccountTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token(account, AccountTokenPurpose.PASSWORD_RESET)));
        service.processPasswordResetRequest(account.getEmail());
        verifyNoInteractions(mail); verify(tokens, never()).save(any());
    }

    @Test void eligibleResetUsesThirtyMinuteLink() {
        var account = account();
        when(accounts.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        service.processPasswordResetRequest(account.getEmail());
        var captor = ArgumentCaptor.forClass(AccountToken.class); verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test void wrongCurrentPasswordCannotChangePasswordOrEmail() {
        var account = account(); stubCurrent(account);
        assertThatThrownBy(() -> service.changePassword("reader", "wrong", "New-password-123"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.requestEmailChange("reader", "wrong", "new@example.com"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mail, tokens, audit); assertThat(account.getSessionVersion()).isZero();
    }

    @Test void passwordChangeRequiresDifferentPasswordAndRevokesAllSessions() {
        var account = account(); stubCurrent(account);
        assertThatThrownBy(() -> service.changePassword("reader", "Old-password-123", "Old-password-123"))
                .isInstanceOf(ResponseStatusException.class);
        service.changePassword("reader", "Old-password-123", "New-password-123");
        assertThat(account.getSessionVersion()).isEqualTo(1);
        assertThat(encoder.matches("New-password-123", account.getPasswordHash())).isTrue();
        verify(mail).notice(account.getEmail(), "Password changed; all sessions signed out");
    }

    @Test void emailRequestKeepsOldAddressUntilConfirmation() {
        var account = account(); stubCurrent(account);
        service.requestEmailChange("reader", "Old-password-123", "New@Example.com");
        assertThat(account.getEmail()).isEqualTo("reader@example.com");
        verify(mail).action(eq("new@example.com"), eq("EMAIL_CHANGE"), anyString());
        verify(mail).notice(eq("reader@example.com"), contains("Email change requested"));
    }

    @Test void emailConfirmationChangesAddressAndNotifiesBothAddresses() throws Exception {
        var account = account(); var token = token(account, AccountTokenPurpose.EMAIL_CHANGE);
        token.setEmail("new@example.com"); stubToken(token);
        service.confirmEmail(RAW);
        assertThat(account.getEmail()).isEqualTo("new@example.com"); assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getSessionVersion()).isEqualTo(1);
        verify(mail).notice(eq("reader@example.com"), contains("Email changed"));
        verify(mail).notice("new@example.com", "Email verified");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void lastActiveAdminCannotBeDisabledOrDemoted(boolean disable) {
        var account = account(); account.setRole(UserRole.ADMIN);
        when(accounts.lockAdministrators()).thenReturn(List.of(account));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.changeAccess(1L, disable ? false : null, disable ? null : UserRole.VIEWER))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("last active administrator");
        assertThat(account.getRole()).isEqualTo(UserRole.ADMIN); assertThat(account.isActive()).isTrue();
        verifyNoInteractions(mail, audit);
    }

    @Test void secondAdminAllowsDeactivationAndSessionRevocation() {
        var account = account(); account.setRole(UserRole.ADMIN);
        var second = account(); second.setId(2L); second.setRole(UserRole.ADMIN);
        when(accounts.lockAdministrators()).thenReturn(List.of(account, second));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account)); when(accounts.save(account)).thenReturn(account);
        service.changeAccess(1L, false, null);
        assertThat(account.loginAllowed()).isFalse(); assertThat(account.getSessionVersion()).isEqualTo(1);
        verify(audit).record(1L, "ACCESS_CHANGED");
    }

    @Test void unverifiedInvitationsCannotBeActivatedThroughAdminEndpoint() {
        var account = account(); account.setEmailVerified(false); account.setActive(false);
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.changeAccess(1L, true, null)).isInstanceOf(ResponseStatusException.class);
        assertThat(account.isActive()).isFalse();
    }

    @Test void resendRevokesPreviousInvitationAndIsThrottled() {
        var account = account(); account.setEmailVerified(false); account.setActive(false);
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        var old = token(account, AccountTokenPurpose.INVITATION); old.setIssuedAt(NOW.minusSeconds(120));
        when(tokens.findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(1L, AccountTokenPurpose.INVITATION)).thenReturn(Optional.of(old));
        service.resendInvitation(1L);
        assertThat(old.getUsedAt()).isEqualTo(NOW); assertThat(account.getSessionVersion()).isEqualTo(1);
        old.setIssuedAt(NOW.minusSeconds(17).minusMillis(100));
        assertThatThrownBy(() -> service.resendInvitation(1L))
                .isInstanceOfSatisfying(com.company.integration_catalog.exception.ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(429);
                    assertThat(ex.getCode()).isEqualTo("INVITATION_RATE_LIMITED");
                    assertThat(ex.getRetryAfterSeconds()).isEqualTo(43L);
                });
    }

    @Test void adminResetBlocksOldPasswordUntilResetCompletes() throws Exception {
        var account = account();
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        service.adminPasswordReset(1L);
        assertThat(account.loginAllowed()).isFalse(); assertThat(account.isPasswordResetRequired()).isTrue();
        var captor = ArgumentCaptor.forClass(AccountToken.class); verify(tokens).save(captor.capture());
        var token = captor.getValue();
        stubToken(token);
        service.resetPassword(RAW, "New-password-123");
        assertThat(account.isPasswordResetRequired()).isFalse(); assertThat(account.loginAllowed()).isTrue();
    }

    @Test void forcedResetCannotLockOutLastAdmin() {
        var account = account(); account.setRole(UserRole.ADMIN);
        when(accounts.lockAdministrators()).thenReturn(List.of(account));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.adminPasswordReset(1L)).isInstanceOf(ResponseStatusException.class);
        assertThat(account.loginAllowed()).isTrue(); verifyNoInteractions(tokens, mail);
    }

    private void stubCurrent(UserAccount account) {
        when(accounts.findByUsername("reader")).thenReturn(Optional.of(account));
        when(accounts.lockById(1L)).thenReturn(Optional.of(account));
    }
    private void stubToken(AccountToken token) throws Exception {
        token.setTokenHash(hash(RAW));
        when(tokens.findAccountIdByTokenHash(hash(RAW))).thenReturn(Optional.of(token.getAccount().getId()));
        when(tokens.lockByHash(hash(RAW))).thenReturn(Optional.of(token));
        when(accounts.lockById(token.getAccount().getId())).thenReturn(Optional.of(token.getAccount()));
    }
    private AccountToken token(UserAccount account, AccountTokenPurpose purpose) {
        AccountToken token = new AccountToken(); token.setAccount(account); token.setPurpose(purpose);
        token.setEmail(account.getEmail()); token.setSessionVersion(account.getSessionVersion());
        token.setIssuedAt(NOW); token.setExpiresAt(NOW.plusSeconds(1800)); return token;
    }
    private UserAccount account() {
        UserAccount account = new UserAccount(); account.setId(1L); account.setUsername("reader");
        account.setRole(UserRole.EDITOR); account.setEmail("reader@example.com"); account.setEmailVerified(true);
        account.setPasswordHash(encoder.encode("Old-password-123")); return account;
    }
    private String hash(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
