package com.company.integration_catalog.security;

import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Component
public class CredentialCrypto {
    private static final String FORMAT = "AES-256-GCM-v1";
    private final SecretKeySpec key;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public CredentialCrypto(@Value("${catalog.credentials.key:}") String encodedKey, ObjectMapper mapper) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("CATALOG_CREDENTIALS_KEY must be a Base64-encoded 32-byte key");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("CATALOG_CREDENTIALS_KEY must be a Base64-encoded 32-byte key");
        }
        this.key = new SecretKeySpec(bytes, "AES");
        java.util.Arrays.fill(bytes, (byte) 0);
        this.mapper = mapper;
    }

    public boolean encrypted(Map<String, Object> value) {
        return value != null && value.containsKey("_credentialFormat");
    }

    public Map<String, Object> encrypt(Map<String, Object> plaintext, EnvironmentConfig config) {
        return encrypt(plaintext, context(config));
    }

    public Map<String, Object> encryptMail(Map<String, Object> plaintext, java.util.UUID id) {
        return encrypt(plaintext, ("mail-outbox-v1|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> encrypt(Map<String, Object> plaintext, byte[] context) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(context);
            byte[] ciphertext = cipher.doFinal(mapper.writeValueAsBytes(plaintext));
            return Map.of("_credentialFormat", FORMAT, "iv", Base64.getEncoder().encodeToString(iv),
                    "ciphertext", Base64.getEncoder().encodeToString(ciphertext));
        } catch (Exception ex) {
            throw new IllegalStateException("Credential encryption failed");
        }
    }

    public Map<String, Object> decrypt(Map<String, Object> stored, EnvironmentConfig config) {
        return decrypt(stored, context(config));
    }

    public Map<String, Object> decryptMail(Map<String, Object> stored, java.util.UUID id) {
        return decrypt(stored, ("mail-outbox-v1|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> decrypt(Map<String, Object> stored, byte[] context) {
        if (stored == null || stored.isEmpty()) return Map.of();
        try {
            if (!FORMAT.equals(stored.get("_credentialFormat"))) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode((String) stored.get("iv"));
            if (iv.length != 12) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(context);
            return mapper.readValue(cipher.doFinal(Base64.getDecoder().decode((String) stored.get("ciphertext"))),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {

            throw new IllegalStateException("Credential decryption failed");
        }
    }

    private byte[] context(EnvironmentConfig config) {
        return (FORMAT + "|" + config.getIntegration().getId() + "|" + config.getEnvironmentType()
                + "|" + config.getConfigTarget() + "|" + config.getAuthType()).getBytes(StandardCharsets.UTF_8);
    }
}
