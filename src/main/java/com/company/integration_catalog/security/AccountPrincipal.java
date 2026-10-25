package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.UserAccount;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

public class AccountPrincipal extends User {
    private final Long accountId;
    private final long sessionVersion;

    public AccountPrincipal(UserAccount account) {
        super(account.getUsername(), account.getPasswordHash(), account.loginAllowed(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        this.accountId = account.getId();
        this.sessionVersion = account.getSessionVersion();
    }
    public Long getAccountId() { return accountId; }
    public long getSessionVersion() { return sessionVersion; }
}
