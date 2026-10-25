package com.company.integration_catalog.model.entity;

import com.company.integration_catalog.model.enums.SchemaType;
import com.company.integration_catalog.model.enums.PayloadFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

@Entity
@Table(name = "mapping_schemas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MappingSchema extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private Integration integration;

    /**
     * Şemanın rolü nedir?
     * Örn: SOURCE_TO_MW_REQUEST, MW_TO_DEST_REQUEST, DEST_TO_MW_RESPONSE, MW_TO_SOURCE_RESPONSE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schema_type", nullable = false)
    private SchemaType schemaType;

    /**
     * Şemanın tüm alanlarını, tiplerini ve hiyerarşisini tutan dinamik JSON yapısı.
     * AI bu yapıyı doğrudan okuyarak mantıksal eşleme üretecek.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_definition", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> schemaDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16) default 'JSON'")
    @Builder.Default
    private PayloadFormat format = PayloadFormat.JSON;

    @Column(columnDefinition = "text")
    private String rawContent;

    @Column(length = 255)
    private String fileName;

    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] sampleFile;

    private String csvDelimiter;

    private Boolean csvHasHeader;
}
