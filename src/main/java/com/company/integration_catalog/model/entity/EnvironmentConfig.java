package com.company.integration_catalog.model.entity;

import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "environment_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvironmentConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private Integration integration;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_target", nullable = false)
    private ConfigTarget configTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment_type", nullable = false)
    private EnvironmentType environmentType;

    @Column(name = "url", nullable = false, length = 100)
    private String url;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    private AuthType authType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_credentials", columnDefinition = "jsonb")
    private Map<String, Object> authCredentials;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "retry_interval_ms")
    private Integer retryIntervalMs;
}
