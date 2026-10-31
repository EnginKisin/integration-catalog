package com.company.integration_catalog.model.entity;

import java.util.ArrayList;
import java.util.List;

import com.company.integration_catalog.model.enums.IntegrationStatus;
import com.company.integration_catalog.model.enums.PayloadFormat;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Integration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(columnDefinition = "bigint default 0")
    private Long version;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reviewRequired;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_system_id", nullable = false)
    private CatalogSystem sourceSystem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_system_id", nullable = false)
    private CatalogSystem destinationSystem;

    @Column(name = "has_middleware", nullable = false)
    private Boolean hasMiddleware = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "middleware_system_id")
    private CatalogSystem middlewareSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_payload_format", nullable = false)
    private PayloadFormat sourcePayloadFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_payload_format", nullable = false)
    private PayloadFormat destinationPayloadFormat;

    @Column(name = "is_sync", nullable = false)
    private Boolean isSync = false;

    @OneToMany(mappedBy = "integration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EnvironmentConfig> environmentConfigs = new ArrayList<>();

    @OneToMany(mappedBy = "integration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MappingSchema> mappingSchemas = new ArrayList<>();

    @OneToMany(mappedBy = "integration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FieldMapping> fieldMappings = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IntegrationStatus status = IntegrationStatus.DRAFT;
}
