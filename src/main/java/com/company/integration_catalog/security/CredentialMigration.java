package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.enums.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class CredentialMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final CredentialCrypto crypto;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public CredentialMigration(JdbcTemplate jdbc, CredentialCrypto crypto, ObjectMapper mapper,
            @Value("${catalog.credentials.migrate:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var configs = jdbc.query("SELECT id, integration_id, environment_type, config_target, auth_type, auth_credentials::text AS credentials "
                + "FROM environment_configs WHERE auth_credentials IS NOT NULL FOR UPDATE", (row, index) -> {
            EnvironmentConfig config = new EnvironmentConfig();
            config.setId(row.getLong("id"));
            config.setIntegration(Integration.builder().id(row.getLong("integration_id")).build());
            config.setEnvironmentType(EnvironmentType.valueOf(row.getString("environment_type")));
            config.setConfigTarget(ConfigTarget.valueOf(row.getString("config_target")));
            config.setAuthType(AuthType.valueOf(row.getString("auth_type")));
            try {
                config.setAuthCredentials(mapper.readValue(row.getString("credentials"),
                        new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ex) {
                throw new IllegalStateException("Invalid stored credential data");
            }
            return config;
        });
        for (EnvironmentConfig config : configs) {
            if (crypto.encrypted(config.getAuthCredentials())) {
                // Fail startup with a wrong key or corrupted ciphertext rather than serving broken secrets.
                crypto.decrypt(config.getAuthCredentials(), config);
                continue;
            }
            if (!enabled) {
                throw new IllegalStateException("Legacy credential data exists; back up the database and enable CATALOG_CREDENTIALS_MIGRATE for one startup");
            }
            String encrypted;
            try {
                Map<String, Object> value = crypto.encrypt(config.getAuthCredentials(), config);
                encrypted = value == null ? null : mapper.writeValueAsString(value);
            } catch (Exception ex) {
                throw new IllegalStateException("Credential migration failed");
            }
            jdbc.update("UPDATE environment_configs SET auth_credentials = CAST(? AS jsonb) WHERE id = ?",
                    encrypted, config.getId());
        }
    }
}
