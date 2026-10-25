package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CredentialCryptoTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CredentialCrypto crypto = new CredentialCrypto(Base64.getEncoder().encodeToString(new byte[32]), mapper);

    @Test
    void encryptsJsonWithRandomIvAndPreservesItsContents() throws Exception {
        EnvironmentConfig config = config();
        Map<String, Object> secret = Map.of("password", "özel-secret", "options", List.of(1, true));
        var encrypted = crypto.encrypt(secret, config);
        assertThat(mapper.writeValueAsString(encrypted)).doesNotContain("özel-secret", "password", "options");
        assertThat(encrypted).isNotEqualTo(crypto.encrypt(secret, config));
        assertThat(crypto.decrypt(encrypted, config)).isEqualTo(secret);
    }

    @ParameterizedTest
    @ValueSource(strings = {"integration", "environment", "target", "auth", "ciphertext", "iv", "format", "key"})
    void rejectsTamperingWrongContextAndWrongKey(String change) {
        EnvironmentConfig config = config();
        Map<String, Object> stored = new HashMap<>(crypto.encrypt(Map.of("secret", "private"), config));
        CredentialCrypto reader = crypto;
        switch (change) {
            case "integration" -> config.setIntegration(Integration.builder().id(99L).build());
            case "environment" -> config.setEnvironmentType(EnvironmentType.PROD);
            case "target" -> config.setConfigTarget(ConfigTarget.MIDDLEWARE);
            case "auth" -> config.setAuthType(AuthType.BASIC_AUTH);
            case "ciphertext" -> {
                byte[] bytes = Base64.getDecoder().decode((String) stored.get("ciphertext"));
                bytes[0] ^= 1;
                stored.put("ciphertext", Base64.getEncoder().encodeToString(bytes));
            }
            case "iv" -> stored.put("iv", "invalid");
            case "format" -> stored.put("_credentialFormat", "unsupported");
            case "key" -> {
                byte[] bytes = new byte[32];
                Arrays.fill(bytes, (byte) 1);
                reader = new CredentialCrypto(Base64.getEncoder().encodeToString(bytes), mapper);
            }
        }
        CredentialCrypto finalReader = reader;
        assertThatThrownBy(() -> finalReader.decrypt(stored, config))
                .isInstanceOf(IllegalStateException.class).hasMessage("Credential decryption failed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64", "YQ=="})
    void rejectsMissingOrInvalidKeys(String key) {
        assertThatThrownBy(() -> new CredentialCrypto(key, mapper)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neverTreatsPlaintextAsDecryptableCredentials() {
        assertThatThrownBy(() -> crypto.decrypt(Map.of("password", "private"), config()))
                .hasMessage("Credential decryption failed");
        assertThat(crypto.encrypt(Map.of(), config())).isNull();
        assertThat(crypto.decrypt(null, config())).isEmpty();
    }

    static EnvironmentConfig config() {
        return EnvironmentConfig.builder().id(1L).integration(Integration.builder().id(1L).build())
                .environmentType(EnvironmentType.DEV).configTarget(ConfigTarget.DESTINATION)
                .authType(AuthType.API_KEY).build();
    }
}
