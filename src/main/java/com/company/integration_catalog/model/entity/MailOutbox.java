package com.company.integration_catalog.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mail_outbox", indexes = @Index(columnList = "sent_at,failed_at,next_attempt_at"))
@Getter @Setter @NoArgsConstructor
public class MailOutbox {
    @Id private UUID id = UUID.randomUUID();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private Map<String, Object> encryptedPayload;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "failed_at") private Instant failedAt;
    private int attempts;
}
