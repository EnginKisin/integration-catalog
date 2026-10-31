package com.company.integration_catalog.service;

import com.company.integration_catalog.model.entity.MailOutbox;
import com.company.integration_catalog.repository.MailOutboxRepository;
import com.company.integration_catalog.security.CredentialCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Clock;
import java.util.Map;

@Service
public class AccountMailService {
    private final MailOutboxRepository outbox;
    private final CredentialCrypto crypto;
    private final Clock clock;
    private final String actionUrl;

    public AccountMailService(MailOutboxRepository outbox, CredentialCrypto crypto, Clock clock,
            @Value("${catalog.accounts.action-url:http://localhost:5173/account/action}") String actionUrl) {
        URI uri = URI.create(actionUrl);
        if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null || !(uri.getScheme().equals("https")
                || (uri.getScheme().equals("http") && (uri.getHost().equals("localhost") || uri.getHost().equals("127.0.0.1"))))) {
            throw new IllegalStateException("Account action URL must use HTTPS (HTTP only for localhost), without query or fragment");
        }
        this.outbox = outbox; this.crypto = crypto; this.clock = clock; this.actionUrl = actionUrl;
    }

    @Transactional
    public void action(String email, String purpose, String token) {
        enqueue(email, "Integration Catalog: " + purpose,
                "Complete your account action using this link:\n" + actionUrl
                        + "#purpose=" + purpose + "&token=" + token
                        + "\nIf you did not request this, ignore this message. Never share this link.");
    }

    @Transactional
    public void notice(String email, String action) {
        if (email != null) enqueue(email, "Integration Catalog account notification",
                "Account action: " + action + ". If this was unexpected, contact your administrator.");
    }

    @Transactional
    public void requestPasswordReset(String email) {
        MailOutbox message = new MailOutbox();
        message.setEncryptedPayload(crypto.encryptMail(Map.of("kind", "PASSWORD_RESET_REQUEST", "email", email), message.getId()));
        message.setNextAttemptAt(clock.instant());
        outbox.save(message);
    }

    @Transactional(readOnly = true)
    public DeliveryStatus status() {
        return new DeliveryStatus(outbox.countBySentAtIsNullAndFailedAtIsNull(), outbox.countByFailedAtIsNotNull());
    }

    public record DeliveryStatus(long pending, long failed) {}

    private void enqueue(String email, String subject, String text) {
        MailOutbox message = new MailOutbox();
        message.setEncryptedPayload(crypto.encryptMail(Map.of("to", email, "subject", subject, "text", text), message.getId()));
        message.setNextAttemptAt(clock.instant());
        outbox.save(message);
    }
}
