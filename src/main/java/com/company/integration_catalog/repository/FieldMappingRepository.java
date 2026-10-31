package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.FieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldMappingRepository extends JpaRepository<FieldMapping, Long> {

    List<FieldMapping> findByIntegrationId(Long integrationId);

    List<FieldMapping> findByIntegrationIdAndIsAiGenerated(Long integrationId, Boolean isAiGenerated);

    void deleteByIntegrationId(Long integrationId);
}