package com.company.integration_catalog.security;

import com.company.integration_catalog.model.enums.UserPermission;
import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
@RequiredArgsConstructor
public class CredentialAccess {
    private final UserAccountRepository accounts;

    @Transactional(readOnly = true)
    public boolean allowed(Authentication authentication, UserPermission permission) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return false;
        return accounts.findByUsername(authentication.getName()).map(account ->
                account.loginAllowed() && (account.getRole() == UserRole.ADMIN
                        || (account.getPermissions().contains(permission)
                        && (permission == UserPermission.VIEW_CREDENTIALS || account.getRole() == UserRole.EDITOR))))
                .orElse(false);
    }

    public void require(UserPermission permission) {
        if (!allowed(SecurityContextHolder.getContext().getAuthentication(), permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credential permission required");
        }
    }
}
