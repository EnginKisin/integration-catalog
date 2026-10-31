package com.company.integration_catalog.model.entity;

import com.company.integration_catalog.model.enums.SchemaType;
import com.company.integration_catalog.model.enums.PayloadFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "schema_type", nullable = false)
    private SchemaType schemaType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_definition", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> schemaDefinition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> samplePayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> targetSchemaDefinition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> targetSamplePayload;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PayloadFormat targetFormat;

    @Column(columnDefinition = "text")
    private String targetRawContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ColumnDefault("'JSON'")
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
