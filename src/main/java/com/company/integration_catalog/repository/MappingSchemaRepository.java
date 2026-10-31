package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.model.enums.SchemaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MappingSchemaRepository extends JpaRepository<MappingSchema, Long> {

    Optional<MappingSchema> findByIdAndIntegrationId(Long id, Long integrationId);

    List<MappingSchema> findByIntegrationId(Long integrationId);

    Optional<MappingSchema> findByIntegrationIdAndSchemaType(Long integrationId, SchemaType schemaType);
}
