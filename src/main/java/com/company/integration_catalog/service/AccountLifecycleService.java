package com.company.integration_catalog.service;

import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.company.integration_catalog.exception.ApiException;
import com.company.integration_catalog.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountLifecycleService {
    private final UserAccountRepository accounts;
    private final AccountTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final AccountMailService mail;
    private final AccountAuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public AccountMailService.DeliveryStatus mailStatus() { return mail.status(); }

    @Transactional
    public UserAccount invite(String username, String email, UserRole role) {
        email = normalize(email);
        if (accounts.findByUsername(username).isPresent())
            throw ApiException.field(409, "USERNAME_TAKEN", "username");
        if (accounts.findByEmail(email).isPresent())
            throw ApiException.field(409, "EMAIL_UNAVAILABLE", "email");
        UserAccount account = new UserAccount();
        account.setUsername(username); account.setEmail(email); account.setRole(role);
        account.setActive(false); account.setEmailVerified(false);
        account.setPasswordHash(encoder.encode(randomToken()));
        account = accounts.save(account);
        issue(account, AccountTokenPurpose.INVITATION, email);
        audit.record(account.getId(), "INVITED");
        return account;
    }

    @Transactional
    public void resendInvitation(Long id) {
        var account = locked(id);
        if (account.isEmailVerified() || account.getEmail() == null)
            throw new ApiException(409, "INVITATION_NOT_PENDING", "Account is not awaiting invitation acceptance");
        requireCooldown(account, AccountTokenPurpose.INVITATION, "INVITATION_RATE_LIMITED");
        account.revokeSessions();
        issue(account, AccountTokenPurpose.INVITATION, account.getEmail());
        audit.record(id, "INVITATION_RESENT");
    }

    @Transactional
    public void acceptInvitation(String raw, String password) {
        var token = validToken(raw, AccountTokenPurpose.INVITATION);
        var account = token.getAccount();
        if (account.isEmailVerified() || !Objects.equals(account.getEmail(), token.getEmail())) throw invalidToken();
        account.setPasswordHash(encoder.encode(password)); account.setEmailVerified(true); account.setActive(true);
        finish(token, "INVITATION_ACCEPTED");
        mail.notice(account.getEmail(), "Account activated");
    }

    @Transactional
    public void requestPasswordReset(String email) {
        mail.requestPasswordReset(normalize(email));
    }

    @Transactional
    public void processPasswordResetRequest(String email) {
        var found = accounts.findByEmail(normalize(email));
        if (found.isEmpty()) return;
        var account = locked(found.get().getId());
        if (!account.isActive() || !account.isEmailVerified() || recent(account, AccountTokenPurpose.PASSWORD_RESET)) return;
        issue(account, AccountTokenPurpose.PASSWORD_RESET, account.getEmail());
        audit.record(account.getId(), "PASSWORD_RESET_REQUESTED");
    }

    @Transactional
    public void adminPasswordReset(Long id) {
        var admins = accounts.lockAdministrators();
        var account = locked(id);
        if (account.getEmail() == null || !account.isEmailVerified() || !account.isActive())
            throw new ApiException(409, "ACCOUNT_EMAIL_REQUIRED", "Account requires an active verified email");
        if (account.getRole() == UserRole.ADMIN && account.loginAllowed()
                && admins.stream().filter(UserAccount::loginAllowed).count() <= 1)
            throw new ApiException(409, "LAST_ADMIN_RESET_RESTRICTED", "The last active administrator must use self-service password recovery");
        requireCooldown(account, AccountTokenPurpose.PASSWORD_RESET, "PASSWORD_RESET_RATE_LIMITED");
        account.revokeSessions();
        account.setPasswordResetRequired(true);
        issue(account, AccountTokenPurpose.PASSWORD_RESET, account.getEmail());
        audit.record(id, "ADMIN_PASSWORD_RESET_REQUESTED");
    }

    @Transactional
    public void resetPassword(String raw, String password) {
        var token = validToken(raw, AccountTokenPurpose.PASSWORD_RESET);
        var account = token.getAccount();
        if (!account.isActive() || !account.isEmailVerified() || !Objects.equals(account.getEmail(), token.getEmail())) throw invalidToken();
        rejectSamePassword(account, password);
        account.setPasswordHash(encoder.encode(password));
        account.setPasswordResetRequired(false);
        finish(token, "PASSWORD_RESET");
        mail.notice(account.getEmail(), "Password reset; all sessions signed out");
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        var account = current(username);
        verifyPassword(account, currentPassword); rejectSamePassword(account, newPassword);
        account.setPasswordHash(encoder.encode(newPassword)); account.revokeSessions();
        mail.notice(account.getEmail(), "Password changed; all sessions signed out");
        audit.record(account.getId(), "PASSWORD_CHANGED");
    }

    @Transactional
    public void requestEmailChange(String username, String currentPassword, String newEmail) {
        var account = current(username);
        verifyPassword(account, currentPassword);
        newEmail = normalize(newEmail);
        if (newEmail.equals(account.getEmail()) || accounts.findByEmail(newEmail).isPresent())
            throw ApiException.field(409, "EMAIL_UNAVAILABLE", "email");
        requireCooldown(account, AccountTokenPurpose.EMAIL_CHANGE, "EMAIL_CHANGE_RATE_LIMITED");
        issue(account, AccountTokenPurpose.EMAIL_CHANGE, newEmail);
        mail.notice(account.getEmail(), "Email change requested; your current email remains active until verification");
        audit.record(account.getId(), "EMAIL_CHANGE_REQUESTED");
    }

    @Transactional
    public void confirmEmail(String raw) {
        var token = validToken(raw, AccountTokenPurpose.EMAIL_CHANGE);
        var account = token.getAccount();
        if (!account.loginAllowed() || accounts.findByEmail(token.getEmail()).isPresent()) throw invalidToken();
        String previous = account.getEmail();
        account.setEmail(token.getEmail()); account.setEmailVerified(true);
        finish(token, "EMAIL_CHANGED");
        mail.notice(previous, "Email changed; all sessions signed out");
        mail.notice(account.getEmail(), "Email verified");
    }

    @Transactional
    public UserAccount changeAccess(Long id, Boolean active, UserRole role) {
        var admins = accounts.lockAdministrators();
        var account = locked(id);
        boolean removesAdmin = account.getRole() == UserRole.ADMIN && account.loginAllowed()
                && (Boolean.FALSE.equals(active) || (role != null && role != UserRole.ADMIN));
        if (removesAdmin && admins.stream().filter(UserAccount::loginAllowed).count() <= 1)
            throw new ApiException(409, "LAST_ADMIN_REQUIRED", "The last active administrator must be retained");
        if (Boolean.TRUE.equals(active) && account.getEmail() != null && !account.isEmailVerified())
            throw new ApiException(409, "EMAIL_VERIFICATION_REQUIRED", "Email verification is required before activation");
        if (active != null) account.setActive(active);
        if (role != null) account.setRole(role);
        account.revokeSessions();
        audit.record(id, "ACCESS_CHANGED");
        mail.notice(account.getEmail(), "Account access changed");
        return accounts.save(account);
    }

    @Transactional
    public void revokeSessions(Long id) {
        locked(id).revokeSessions();
        audit.record(id, "SESSIONS_REVOKED");
    }

    private void issue(UserAccount account, AccountTokenPurpose purpose, String email) {
        String raw = randomToken();
        AccountToken token = new AccountToken();
        token.setAccount(account); token.setTokenHash(hash(raw)); token.setPurpose(purpose); token.setEmail(email);
        token.setSessionVersion(account.getSessionVersion()); token.setIssuedAt(clock.instant());
        token.setExpiresAt(clock.instant().plus(purpose == AccountTokenPurpose.INVITATION ? Duration.ofHours(24) : Duration.ofMinutes(30)));
        tokens.findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(account.getId(), purpose)
                .ifPresent(old -> old.setUsedAt(clock.instant()));
        tokens.save(token);
        mail.action(email, purpose.name(), raw);
    }

    private AccountToken validToken(String raw, AccountTokenPurpose purpose) {
        String hash = hash(raw);
        Long accountId = tokens.findAccountIdByTokenHash(hash).orElseThrow(this::invalidToken);
        var account = locked(accountId);
        var token = tokens.lockByHash(hash).orElseThrow(this::invalidToken);
        if (token.getPurpose() != purpose || token.getUsedAt() != null || !token.getExpiresAt().isAfter(clock.instant())
                || token.getSessionVersion() != account.getSessionVersion()) throw invalidToken();
        token.setAccount(account);
        return token;
    }

    private void finish(AccountToken token, String action) {
        token.setUsedAt(clock.instant()); token.getAccount().revokeSessions();
        audit.record(token.getAccount().getId(), action);
    }
    private void requireCooldown(UserAccount account, AccountTokenPurpose purpose, String code) {
        long retryAfter = tokens.findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(account.getId(), purpose)
                .map(token -> {
                    Duration remaining = Duration.between(clock.instant(), token.getIssuedAt().plusSeconds(60));
                    return remaining.isNegative() || remaining.isZero() ? 0L
                            : remaining.getSeconds() + (remaining.getNano() > 0 ? 1L : 0L);
                }).orElse(0L);
        if (retryAfter > 0) throw new ApiException(429, code, ErrorCode.message(code), Map.of(), List.of(), retryAfter);
    }
    private boolean recent(UserAccount account, AccountTokenPurpose purpose) {
        return tokens.findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(account.getId(), purpose)
                .map(token -> token.getIssuedAt().plusSeconds(60).isAfter(clock.instant())).orElse(false);
    }
    private UserAccount current(String username) {
        var account = accounts.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found"));
        account = locked(account.getId());
        if (!account.loginAllowed()) throw new ApiException(403, "ACCOUNT_UNAVAILABLE", "Account unavailable");
        return account;
    }
    private UserAccount locked(Long id) { return accounts.lockById(id).orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found")); }
    private void verifyPassword(UserAccount account, String password) {
        if (!encoder.matches(password, account.getPasswordHash()))
            throw ApiException.field(400, "CURRENT_PASSWORD_INVALID", "currentPassword");
    }
    private void rejectSamePassword(UserAccount account, String password) {
        if (encoder.matches(password, account.getPasswordHash()))
            throw ApiException.field(400, "PASSWORD_REUSE", "newPassword");
    }
    private ApiException invalidToken() {
        return new ApiException(400, "ACCOUNT_LINK_INVALID", "Invalid or expired account link");
    }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Token hashing unavailable"); }
    }
}
