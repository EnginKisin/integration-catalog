package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.Destination;
import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.Middleware;
import com.company.integration_catalog.model.entity.Source;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.IntegrationStatus;
import com.company.integration_catalog.repository.EnvironmentConfigRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.service.IntegrationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final EnvironmentConfigRepository configRepository;
    private final IntegrationMapper integrationMapper;

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationResponseDTO> getAllIntegrations(String keyword) {
        List<Integration> integrations = (keyword != null && !keyword.trim().isEmpty())
                ? integrationRepository.searchIntegrations(keyword)
                : integrationRepository.findAll();

        return integrations.stream()
                .map(integrationMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public IntegrationResponseDTO getIntegrationById(Long id) {
        Integration integration = integrationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + id));
        return integrationMapper.toResponse(integration);
    }

    @Override
    @Transactional
    public IntegrationResponseDTO createIntegration(IntegrationRequestDTO request) {
        Integration integration = integrationMapper.toEntity(request);
        Integration savedIntegration = integrationRepository.save(integration);
        return integrationMapper.toResponse(savedIntegration);
    }

    @Override
    @Transactional
    public IntegrationResponseDTO updateIntegration(Long id, IntegrationRequestDTO request) {
        Integration existingIntegration = integrationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + id));

        existingIntegration.setName(request.getName());
        existingIntegration.setDescription(request.getDescription());
        
        // Yeni Nesne Atamaları:
        existingIntegration.setSource(request.getSourceId() != null ? Source.builder().id(request.getSourceId()).build() : null);
        existingIntegration.setDestination(request.getDestinationId() != null ? Destination.builder().id(request.getDestinationId()).build() : null);
        existingIntegration.setMiddleware(request.getMiddlewareId() != null ? Middleware.builder().id(request.getMiddlewareId()).build() : null);
        
        existingIntegration.setSourcePayloadFormat(request.getSourcePayloadFormat());
        existingIntegration.setDestinationPayloadFormat(request.getDestinationPayloadFormat());
        existingIntegration.setHasMiddleware(request.getHasMiddleware());

        Integration updatedIntegration = integrationRepository.save(existingIntegration);
        return integrationMapper.toResponse(updatedIntegration);
    }

    @Override
    @Transactional
    public void saveEnvironmentConfigs(Long integrationId, List<EnvironmentConfigRequestDTO> configRequests) {
        Integration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + integrationId));

        // Adım 2 her kaydedildiğinde eski konfigürasyonları temizleyip sıfırdan güncel listeyi yazıyoruz (Idempotent işlem)
        List<EnvironmentConfig> oldConfigs = configRepository.findByIntegrationId(integrationId);
        configRepository.deleteAll(oldConfigs);

        List<EnvironmentConfig> newConfigs = configRequests.stream()
                .filter(req -> {
                    // Kritik Kural Kontrolü: Eğer Adım 1'de middleware seçilmediyse, 
                    // yanlışlıkla gönderilen MIDDLEWARE kartı verilerini veritabanına yazma!
                    if (!integration.getHasMiddleware() && req.getConfigTarget() == ConfigTarget.MIDDLEWARE) {
                        return false;
                    }
                    return true;
                })
                .map(req -> integrationMapper.toConfigEntity(req, integration))
                .collect(Collectors.toList());

        configRepository.saveAll(newConfigs);
    }

    @Override
    @Transactional
    public void deleteIntegration(Long id) {
        if (!integrationRepository.existsById(id)) {
            throw new EntityNotFoundException("Integration not found with id: " + id);
        }
        // CascadeType.ALL ve orphanRemoval=true olduğu için alt tablolar (ortamlar, şemalar vb.) otomatik silinecek.
        integrationRepository.deleteById(id);
    }

    // ... Mevcut metotların en altına:
    @Override
    @Transactional
    public void completeIntegration(Long id) {
        Integration integration = integrationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + id));
                
        integration.setStatus(IntegrationStatus.COMPLETED);
        integrationRepository.save(integration);
    }
}