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

    // Hangi şemadan veri alınıyor (Örn: SOURCE_TO_MW_REQUEST)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_schema_id", nullable = false)
    private MappingSchema sourceSchema;

    // Kaynak şemadaki tam alan yolu (Örn: orders[0].customerId veya customer.profile.id)
    @Column(name = "source_field_path", nullable = false, length = 500)
    private String sourceFieldPath;

    // Hangi şemaya veri yazılıyor (Örn: MW_TO_DEST_REQUEST)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_schema_id", nullable = false)
    private MappingSchema targetSchema;

    // Hedef şemadaki tam alan yolu (Örn: dest_order.client_id)
    @Column(name = "target_field_path", nullable = false, length = 500)
    private String targetFieldPath;

    // Eşleşmenin AI tarafından mı yapıldığı yoksa insan eliyle mi kurulduğu bilgisi (İleride analiz için çok değerli)
    @Column(name = "is_ai_generated", nullable = false)
    private Boolean isAiGenerated = false;

    // Eğer AI eşlemesi ise, yapay zekanın güven skoru (Örn: 0.95)
    @Column(name = "ai_confidence_score")
    private Double aiConfidenceScore;
}