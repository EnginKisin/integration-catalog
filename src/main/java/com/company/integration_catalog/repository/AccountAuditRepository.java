package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.AccountAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuditRepository extends JpaRepository<AccountAudit, Long> {}
