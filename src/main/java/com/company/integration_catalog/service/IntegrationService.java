package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.EnvironmentConfigDTO;
import com.company.integration_catalog.dto.IntegrationDTO;

import java.util.List;

public interface IntegrationService {
    
    // Dashboard için listeleme ve arama
    List<IntegrationDTO.Response> getAllIntegrations(String keyword);
    IntegrationDTO.Response getIntegrationById(Long id);
    
    // Wizard Adım 1: Ana entegrasyon kaydı ve güncellemesi
    IntegrationDTO.Response createIntegration(IntegrationDTO.Request request);
    IntegrationDTO.Response updateIntegration(Long id, IntegrationDTO.Request request);
    
    // Wizard Adım 2: Toplu Ortam Konfigürasyonu Kaydetme (Kritik kural kontrolüyle beraber)
    void saveEnvironmentConfigs(Long integrationId, List<EnvironmentConfigDTO.Request> configRequests);
    
    // Entegrasyon silme
    void deleteIntegration(Long id);

    // ... Mevcut metotların hemen altına:
    void completeIntegration(Long id);
    
}