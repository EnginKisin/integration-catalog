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

@Entity @Table(name="mapping_append_receipts", uniqueConstraints=@UniqueConstraint(name="uk_mapping_append_receipt_idempotency",
        columnNames={"integration_id", "created_by", "idempotency_key"}))
@Getter @Setter
public class MappingAppendReceipt {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="integration_id", nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) private Integration integration;
    @Column(name="created_by", nullable=false, length=254) private String createdBy;
    @Column(name="idempotency_key", nullable=false, length=100) private String idempotencyKey;
    @Column(nullable=false, length=64) private String requestHash;
    @Column(nullable=false) private Instant createdAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false, columnDefinition="jsonb") private Map<String,Object> response;
}
