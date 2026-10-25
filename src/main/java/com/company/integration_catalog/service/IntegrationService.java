package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;

import java.util.List;

public interface IntegrationService {
    
    // Dashboard için listeleme ve arama
    List<IntegrationResponseDTO> getAllIntegrations(String keyword);
    IntegrationResponseDTO getIntegrationById(Long id);
    
    // Wizard Adım 1: Ana entegrasyon kaydı ve güncellemesi
    IntegrationResponseDTO createIntegration(IntegrationRequestDTO request);
    IntegrationResponseDTO updateIntegration(Long id, IntegrationRequestDTO request);
    
    // Wizard Adım 2: Toplu Ortam Konfigürasyonu Kaydetme (Kritik kural kontrolüyle beraber)
    void saveEnvironmentConfigs(Long integrationId, List<EnvironmentConfigRequestDTO> configRequests);
    
    // Entegrasyon silme
    void deleteIntegration(Long id);

    // ... Mevcut metotların hemen altına:
    void completeIntegration(Long id);
    IntegrationResponseDTO confirmIntegrationReview(Long id);
    
}
