package com.company.integration_catalog.ai;

import com.company.integration_catalog.model.entity.Integration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity @Table(name="ai_mapping_jobs", uniqueConstraints=@UniqueConstraint(name="uk_ai_mapping_job_idempotency",
        columnNames={"integration_id", "created_by", "idempotency_key"}),
        indexes=@Index(name="ai_mapping_jobs_queue_idx", columnList="status,created_at"))
@Getter @Setter
public class AiJob {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="integration_id", nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) private Integration integration;
    @Column(name="created_by", nullable=false, length=254) private String createdBy;
    @Column(name="idempotency_key", nullable=false, length=100) private String idempotencyKey;
    @Column(nullable=false, length=64) private String requestHash;
    @Column(nullable=false, length=64) private String contextRevision;
    @Column(nullable=false, length=20) private String status;
    @Column(nullable=false, length=40) private String provider;
    @Column(nullable=false, length=150) private String model;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant expiresAt;
    private Instant leaseUntil;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private Map<String,Object> inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private Map<String,Object> result;
    @Column(length=64) private String errorCode;
    @Column(length=500) private String errorMessage;
}
