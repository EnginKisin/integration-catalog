package com.company.integration_catalog.service;

import com.company.integration_catalog.model.entity.UserAccount;
import com.company.integration_catalog.model.enums.UserPermission;
import java.util.Set;
import java.util.HashSet;
import com.company.integration_catalog.exception.ResourceNotFoundException;
import com.company.integration_catalog.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserAccountRepository repository;
    private final AccountAuditService audit;

    @Transactional(readOnly = true)
    public Page<UserAccount> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public UserAccount profile(String username) {
        return repository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found"));
    }

    @Transactional
    public UserAccount setPermissions(Long id, Set<UserPermission> permissions) {
        UserAccount account = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "User not found"));
        account.setPermissions(new HashSet<>(permissions));
        account.revokeSessions();
        audit.record(id, "PERMISSIONS_CHANGED");
        return repository.save(account);
    }
}
