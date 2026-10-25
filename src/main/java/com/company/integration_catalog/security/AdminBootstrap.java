package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.UserAccount;
import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    @Value("${catalog.bootstrap.username:}")
    private String username;
    @Value("${catalog.bootstrap.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (!username.matches("[a-zA-Z0-9._-]{1,100}") || password.isBlank() || password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("Set CATALOG_ADMIN_USERNAME and CATALOG_ADMIN_PASSWORD (12-128 characters) for initial setup");
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        repository.save(admin);
    }
}
