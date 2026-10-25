package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialMigrationTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CredentialCrypto crypto = new CredentialCrypto(Base64.getEncoder().encodeToString(new byte[32]), mapper);

    @SuppressWarnings("unchecked")
    private void rows(EnvironmentConfig config) {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(config));
    }

    @Test
    void legacyDataRequiresExplicitMigration() {
        var config = CredentialCryptoTests.config();
        config.setAuthCredentials(Map.of("secret", "private"));
        rows(config);
        assertThatThrownBy(() -> new CredentialMigration(jdbc, crypto, mapper, false).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Legacy credential data");
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test
    void migrationWritesOnlyEncryptedJson() throws Exception {
        var config = CredentialCryptoTests.config();
        config.setAuthCredentials(Map.of("secret", "private"));
        rows(config);
        new CredentialMigration(jdbc, crypto, mapper, true).run(null);
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), json.capture(), eq(1L));
        assertThat(json.getValue()).doesNotContain("private", "secret");
        Map<String, Object> stored = mapper.readValue(json.getValue(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        assertThat(crypto.decrypt(stored, config)).containsEntry("secret", "private");
    }

    @Test
    void validEncryptedDataIsNotRewritten() {
        var config = CredentialCryptoTests.config();
        config.setAuthCredentials(crypto.encrypt(Map.of("secret", "private"), config));
        rows(config);
        new CredentialMigration(jdbc, crypto, mapper, false).run(null);
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test
    void corruptDataStopsStartupEvenWhenMigrationEnabled() {
        var config = CredentialCryptoTests.config();
        config.setAuthCredentials(Map.of("_credentialFormat", "unsupported"));
        rows(config);
        assertThatThrownBy(() -> new CredentialMigration(jdbc, crypto, mapper, true).run(null))
                .hasMessage("Credential decryption failed");
        verify(jdbc, never()).update(anyString(), any(), any());
    }
}
