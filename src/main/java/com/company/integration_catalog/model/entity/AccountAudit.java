package com.company.integration_catalog.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "account_audit")
@Getter @Setter @NoArgsConstructor
public class AccountAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private Long accountId;
    @Column(nullable = false) private String action;
    @Column(nullable = false) private Instant occurredAt;
}
