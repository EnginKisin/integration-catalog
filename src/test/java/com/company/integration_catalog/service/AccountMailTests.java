package com.company.integration_catalog.service;

import com.company.integration_catalog.model.entity.MailOutbox;
import com.company.integration_catalog.repository.MailOutboxRepository;
import com.company.integration_catalog.security.CredentialCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountMailTests {
    private final Instant now = Instant.parse("2026-10-18T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final MailOutboxRepository outbox = mock(MailOutboxRepository.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final AccountLifecycleService lifecycle = mock(AccountLifecycleService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CredentialCrypto crypto = new CredentialCrypto(Base64.getEncoder().encodeToString(new byte[32]), mapper);
    private final AccountMailService service = new AccountMailService(outbox, crypto, clock, "http://localhost:5173/account/action");

    @Test void queuedLinksAreEncryptedAndNotReturnedToCaller() throws Exception {
        service.action("reader@example.com", "INVITATION", "private-token");
        var captor = org.mockito.ArgumentCaptor.forClass(MailOutbox.class); verify(outbox).save(captor.capture());
        var row = captor.getValue();
        assertThat(mapper.writeValueAsString(row.getEncryptedPayload())).doesNotContain("private-token", "reader@example.com");
        assertThat((String) crypto.decryptMail(row.getEncryptedPayload(), row.getId()).get("text"))
                .contains("#purpose=INVITATION&token=private-token");
        assertThatThrownBy(() -> crypto.decryptMail(row.getEncryptedPayload(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void sentMailPayloadIsPurged() {
        var row = message();
        worker(row).deliver();
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("reader@example.com");
        assertThat(captor.getValue().getText()).isEqualTo("private-link");
        assertThat(row.getSentAt()).isEqualTo(now); assertThat(row.getEncryptedPayload()).isNull();
    }

    @Test void failedDeliveryIsRetriedAndEventuallyPurged() {
        var row = message(); var worker = worker(row);
        doThrow(new MailSendException("must-not-be-logged-private-link")).when(sender).send(any(SimpleMailMessage.class));
        worker.deliver();
        assertThat(row.getAttempts()).isEqualTo(1); assertThat(row.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
        assertThat(row.getEncryptedPayload()).isNotNull(); assertThat(row.getSentAt()).isNull();
        row.setAttempts(7); worker.deliver();
        assertThat(row.getFailedAt()).isEqualTo(now); assertThat(row.getEncryptedPayload()).isNull();
    }

    @Test void publicResetJobsAreProcessedWithoutSendingToUnknownAddressesDirectly() {
        var row = message();
        row.setEncryptedPayload(crypto.encryptMail(Map.of("kind", "PASSWORD_RESET_REQUEST", "email", "reader@example.com"), row.getId()));
        worker(row).deliver();
        verify(lifecycle).processPasswordResetRequest("reader@example.com"); verifyNoInteractions(sender);
        assertThat(row.getSentAt()).isEqualTo(now); assertThat(row.getEncryptedPayload()).isNull();
    }

    @Test void remoteActionLinksRequireHttpsAndRejectFragmentsOrCredentials() {
        for (String url : List.of("http://example.com/action", "https://example.com/action#fragment", "https://user:pass@example.com/action"))
            assertThatThrownBy(() -> new AccountMailService(outbox, crypto, clock, url)).isInstanceOf(IllegalStateException.class);
    }

    private MailOutboxWorker worker(MailOutbox row) {
        when(outbox.findBySentAtIsNullAndFailedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(List.of(row));
        var worker = new MailOutboxWorker(outbox, crypto, sender, clock, lifecycle);
        ReflectionTestUtils.setField(worker, "from", "no-reply@example.com"); return worker;
    }
    private MailOutbox message() {
        var row = new MailOutbox();
        row.setEncryptedPayload(crypto.encryptMail(Map.of("to", "reader@example.com", "subject", "Account", "text", "private-link"), row.getId()));
        return row;
    }
}
