package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentConfigRepository extends JpaRepository<EnvironmentConfig, Long> {

    // Bir entegrasyona ait tüm ortam konfigürasyonlarını getirir
    List<EnvironmentConfig> findByIntegrationId(Long integrationId);

    // Belirli bir entegrasyonun sadece Middleware veya sadece Destination konfigürasyonlarını getirir
    List<EnvironmentConfig> findByIntegrationIdAndConfigTarget(Long integrationId, ConfigTarget configTarget);

    // Nokta atışı sorgu: Bir entegrasyonun TEST ortamındaki MIDDLEWARE bilgisi nedir?
    Optional<EnvironmentConfig> findByIntegrationIdAndConfigTargetAndEnvironmentType(
            Long integrationId, ConfigTarget configTarget, EnvironmentType environmentType);
}