package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;

import java.util.List;

public interface IntegrationService {
    
    List<IntegrationResponseDTO> getAllIntegrations(String keyword);
    IntegrationResponseDTO getIntegrationById(Long id);
    
    IntegrationResponseDTO createIntegration(IntegrationRequestDTO request);
    IntegrationResponseDTO updateIntegration(Long id, IntegrationRequestDTO request);
    
    void saveEnvironmentConfigs(Long integrationId, List<EnvironmentConfigRequestDTO> configRequests);
    
    void deleteIntegration(Long id);

    void completeIntegration(Long id);
    IntegrationResponseDTO confirmIntegrationReview(Long id);
    
}
