package com.company.integration_catalog.model.entity;

import com.company.integration_catalog.model.enums.AccountTokenPurpose;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "account_tokens", indexes = @Index(columnList = "account_id,purpose"))
@Getter @Setter @NoArgsConstructor
public class AccountToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount account;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AccountTokenPurpose purpose;
    @Column(nullable = false, length = 254)
    private String email;
    private long sessionVersion;
    @Column(nullable = false)
    private Instant issuedAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
}
