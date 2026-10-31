package com.company.integration_catalog.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "field_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private Integration integration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_schema_id", nullable = false)
    private MappingSchema sourceSchema;

    @Column(name = "source_field_path", nullable = false, length = 500)
    private String sourceFieldPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_schema_id", nullable = false)
    private MappingSchema targetSchema;

    @Column(name = "target_field_path", nullable = false, length = 500)
    private String targetFieldPath;

    @Column(name = "is_ai_generated", nullable = false)
    private Boolean isAiGenerated = false;

    @Column(name = "ai_confidence_score")
    private Double aiConfidenceScore;

    @Column(length = 24)
    private String origin;

    @Column(length = 36)
    private String aiJobId;

    @Column(length = 40)
    private String aiSuggestionId;
}
