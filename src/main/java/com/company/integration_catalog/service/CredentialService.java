package com.company.integration_catalog.service;

import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.EnvironmentConfigRepository;
import com.company.integration_catalog.security.CredentialAccess;
import com.company.integration_catalog.security.CredentialCrypto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialService {
    private final EnvironmentConfigRepository configs;
    private final CredentialAccess access;
    private final CredentialCrypto crypto;

    @Transactional(readOnly = true)
    public Map<String, Object> reveal(Long integrationId, EnvironmentType environment, ConfigTarget target) {
        access.require(UserPermission.VIEW_CREDENTIALS);
        var config = configs.findByIntegrationIdAndConfigTargetAndEnvironmentType(integrationId, target, environment)
                .orElseThrow(() -> new EntityNotFoundException("Environment configuration not found"));
        Map<String, Object> credentials = crypto.decrypt(config.getAuthCredentials(), config);
        log.info("credential_reveal actor={} integration={} environment={} target={}",
                SecurityContextHolder.getContext().getAuthentication().getName(), integrationId, environment, target);
        return credentials;
    }
}
