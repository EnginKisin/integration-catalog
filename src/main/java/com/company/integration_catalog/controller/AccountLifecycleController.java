package com.company.integration_catalog.controller;

import com.company.integration_catalog.service.AccountLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountLifecycleController {
    private final AccountLifecycleService service;

    @PostMapping("/invitations/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@Valid @RequestBody TokenPasswordRequest request) {
        service.acceptInvitation(request.getToken(), request.getNewPassword());
    }

    @PostMapping("/password-reset/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public GenericMessage resetRequest(@Valid @RequestBody EmailRequest request) {
        service.requestPasswordReset(request.getEmail());
        return new GenericMessage("If the account is eligible, a reset email will be sent");
    }

    @PostMapping("/password-reset/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody TokenPasswordRequest request) {
        service.resetPassword(request.getToken(), request.getNewPassword());
    }

    @PostMapping("/password/change") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest servletRequest) {
        service.changePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
        if (servletRequest.getSession(false) != null) servletRequest.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/email/change") @ResponseStatus(HttpStatus.ACCEPTED)
    public void emailChange(Authentication authentication, @Valid @RequestBody EmailChangeRequest request) {
        service.requestEmailChange(authentication.getName(), request.getCurrentPassword(), request.getEmail());
    }

    @PostMapping("/email/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody TokenRequest request) { service.confirmEmail(request.getToken()); }

    public record GenericMessage(String message) {}

    @Getter @Setter
    public static class TokenRequest {
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") private String token;
    }
    @Getter @Setter
    public static class TokenPasswordRequest extends TokenRequest {
        @NotBlank @Size(min = 12, max = 128) private String newPassword;
    }
    @Getter @Setter
    public static class EmailRequest {
        @NotBlank @Email @Size(max = 254) private String email;
    }
    @Getter @Setter
    public static class EmailChangeRequest extends EmailRequest {
        @NotBlank @Size(max = 128) private String currentPassword;
    }
    @Getter @Setter
    public static class PasswordChangeRequest {
        @NotBlank @Size(max = 128) private String currentPassword;
        @NotBlank @Size(min = 12, max = 128) private String newPassword;
    }
}
