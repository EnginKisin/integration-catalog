package com.company.integration_catalog.service;

import com.company.integration_catalog.repository.MailOutboxRepository;
import com.company.integration_catalog.security.CredentialCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service @RequiredArgsConstructor @Slf4j
public class MailOutboxWorker {
    private final MailOutboxRepository repository;
    private final CredentialCrypto crypto;
    private final JavaMailSender sender;
    private final Clock clock;
    private final AccountLifecycleService lifecycle;
    @Value("${catalog.accounts.mail-from:no-reply@localhost}") private String from;

    @Scheduled(fixedDelayString = "${catalog.accounts.mail-poll-ms:15000}")
    @Transactional
    public void deliver() {
        var messages = repository.findBySentAtIsNullAndFailedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                clock.instant(), PageRequest.of(0, 10));
        for (var row : messages) {
            row.setAttempts(row.getAttempts() + 1);
            try {
                var payload = crypto.decryptMail(row.getEncryptedPayload(), row.getId());
                if ("PASSWORD_RESET_REQUEST".equals(payload.get("kind"))) {
                    lifecycle.processPasswordResetRequest((String) payload.get("email"));
                } else {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(from); message.setTo((String) payload.get("to"));
                    message.setSubject((String) payload.get("subject")); message.setText((String) payload.get("text"));
                    sender.send(message);
                }
                row.setSentAt(clock.instant());
                row.setEncryptedPayload(null);
            } catch (Exception ex) {
                log.warn("Account mail delivery failed id={} attempt={}", row.getId(), row.getAttempts());
                if (row.getAttempts() >= 8) {
                    row.setFailedAt(clock.instant());
                    row.setEncryptedPayload(null);
                } else {
                    row.setNextAttemptAt(clock.instant().plusSeconds(Math.min(3600, 30L << row.getAttempts())));
                }
            }
        }
        repository.saveAll(messages);
    }
}
