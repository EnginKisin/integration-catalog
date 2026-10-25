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

    // Eski String sourceSystem yerine:
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    // Eski String destinationSystem yerine:
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "has_middleware", nullable = false)
    private Boolean hasMiddleware = false;

    // Eğer hasMiddleware true ise seçilecek Middleware objesi:
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "middleware_id")
    private Middleware middleware;

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
