package com.company.integration_catalog.controller;

import com.company.integration_catalog.model.entity.UserAccount;
import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.model.enums.UserPermission;
import java.util.Set;
import com.company.integration_catalog.service.UserAccountService;
import com.company.integration_catalog.service.AccountLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAccountController {
    private final UserAccountService service;
    private final AccountLifecycleService lifecycle;

    @GetMapping("/mail-status")
    public com.company.integration_catalog.service.AccountMailService.DeliveryStatus mailStatus() {
        return lifecycle.mailStatus();
    }

    @GetMapping
    public UserPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.list(PageRequest.of(page, size, Sort.by("id")));
        return new UserPage(result.stream().map(UserResponse::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody InviteUserRequest request) {
        return UserResponse.from(lifecycle.invite(request.getUsername(), request.getEmail(), request.getRole()));
    }

    // A class, rather than a record, avoids generated toString exposing passwords.
    @lombok.Getter
    @lombok.Setter
    public static class InviteUserRequest {
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-zA-Z0-9._-]+")
        private String username;
        @NotBlank @Email @Size(max = 254)
        private String email;
        @NotNull
        private UserRole role;
    }

    @PostMapping("/{id}/invitations/resend") @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@PathVariable Long id) { lifecycle.resendInvitation(id); }

    @PostMapping("/{id}/password-reset") @ResponseStatus(HttpStatus.ACCEPTED)
    public void passwordReset(@PathVariable Long id) { lifecycle.adminPasswordReset(id); }

    @PutMapping("/{id}/active")
    public UserResponse active(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return UserResponse.from(lifecycle.changeAccess(id, request.active(), null));
    }

    @PutMapping("/{id}/role")
    public UserResponse role(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return UserResponse.from(lifecycle.changeAccess(id, null, request.role()));
    }

    @PostMapping("/{id}/sessions/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) { lifecycle.revokeSessions(id); }

    public record ActiveRequest(@NotNull Boolean active) {}
    public record RoleRequest(@NotNull UserRole role) {}

    @PutMapping("/{id}/permissions")
    public UserResponse setPermissions(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return UserResponse.from(service.setPermissions(id, request.permissions()));
    }

    public record PermissionRequest(@NotNull Set<@NotNull UserPermission> permissions) {}
    public record UserPage(List<UserResponse> items, int page, int size, long totalElements, int totalPages) {}

    public record UserResponse(Long id, String username, String email, boolean emailVerified, boolean active, boolean passwordResetRequired,
            UserRole role, Set<UserPermission> permissions) {
        static UserResponse from(UserAccount account) {
            return new UserResponse(account.getId(), account.getUsername(), account.getEmail(), account.isEmailVerified(),
                    account.isActive(), account.isPasswordResetRequired(), account.getRole(), Set.copyOf(account.getPermissions()));
        }
    }
}
