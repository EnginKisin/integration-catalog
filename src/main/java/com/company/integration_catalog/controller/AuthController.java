package com.company.integration_catalog.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.company.integration_catalog.security.CredentialAccess;
import com.company.integration_catalog.model.enums.UserPermission;
import lombok.RequiredArgsConstructor;
import com.company.integration_catalog.service.UserAccountService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final CredentialAccess credentialAccess;
    private final UserAccountService accounts;
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        var account = accounts.profile(authentication.getName());
        return new CurrentUser(authentication.getName(), account.getEmail(), account.isEmailVerified(), account.isActive(),
                authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList(),
                java.util.Arrays.stream(UserPermission.values())
                        .filter(permission -> credentialAccess.allowed(authentication, permission))
                        .map(Enum::name).toList());
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {}
    public record CurrentUser(String username, String email, boolean emailVerified, boolean active,
            List<String> authorities, List<String> permissions) {}
}
