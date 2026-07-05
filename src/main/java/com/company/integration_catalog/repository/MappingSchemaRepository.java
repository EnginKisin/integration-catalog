package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.model.enums.SchemaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MappingSchemaRepository extends JpaRepository<MappingSchema, Long> {

    // Bir entegrasyona ait tüm şemaları (4 adet payload alanını) getirir
    List<MappingSchema> findByIntegrationId(Long integrationId);

    // Belirli bir entegrasyonun spesifik bir şemasını getirir (Örn: SOURCE_TO_MW_REQUEST)
    Optional<MappingSchema> findByIntegrationIdAndSchemaType(Long integrationId, SchemaType schemaType);
}