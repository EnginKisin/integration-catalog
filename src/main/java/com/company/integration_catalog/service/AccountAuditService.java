package com.company.integration_catalog.service;

import com.company.integration_catalog.model.entity.AccountAudit;
import com.company.integration_catalog.repository.AccountAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.Clock;

@Service @RequiredArgsConstructor
public class AccountAuditService {
    private final AccountAuditRepository repository;
    private final Clock clock;
    public void record(Long accountId, String action) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        AccountAudit audit = new AccountAudit();
        audit.setAccountId(accountId); audit.setAction(action); audit.setOccurredAt(clock.instant());
        audit.setActor(authentication == null || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken
                ? "token-holder" : authentication.getName());
        repository.save(audit);
    }
}
