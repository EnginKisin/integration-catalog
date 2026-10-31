package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.CatalogSystem;
import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.IntegrationStatus;
import com.company.integration_catalog.repository.EnvironmentConfigRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.repository.SystemRepository;
import com.company.integration_catalog.service.IntegrationService;
import com.company.integration_catalog.validation.IntegrationCompletionValidator;
import com.company.integration_catalog.security.CredentialCrypto;
import com.company.integration_catalog.security.CredentialAccess;
import com.company.integration_catalog.model.enums.UserPermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final EnvironmentConfigRepository configRepository;
    private final IntegrationMapper integrationMapper;
    private final SystemRepository systemRepository;
    private final IntegrationCompletionValidator completionValidator;
    private final CredentialCrypto credentialCrypto;
    private final CredentialAccess credentialAccess;

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
        assignSystems(integration, request);
        Integration savedIntegration = integrationRepository.save(integration);
        return integrationMapper.toResponse(savedIntegration);
    }

    @Override
    @Transactional
    public IntegrationResponseDTO updateIntegration(Long id, IntegrationRequestDTO request) {
        Integration existingIntegration = integrationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + id));

        boolean technicalChange = !Objects.equals(existingIntegration.getSourceSystem() == null ? null : existingIntegration.getSourceSystem().getId(), request.getSourceSystemId())
                || !Objects.equals(existingIntegration.getDestinationSystem() == null ? null : existingIntegration.getDestinationSystem().getId(), request.getDestinationSystemId())
                || !Objects.equals(existingIntegration.getMiddlewareSystem() == null ? null : existingIntegration.getMiddlewareSystem().getId(), request.getMiddlewareSystemId())
                || !Objects.equals(existingIntegration.getHasMiddleware(), request.getHasMiddleware())
                || !Objects.equals(existingIntegration.getIsSync(), request.getIsSync())
                || existingIntegration.getSourcePayloadFormat() != request.getSourcePayloadFormat()
                || existingIntegration.getDestinationPayloadFormat() != request.getDestinationPayloadFormat();
        assignSystems(existingIntegration, request);
        existingIntegration.setName(request.getName());
        existingIntegration.setDescription(request.getDescription());
        
        existingIntegration.setSourcePayloadFormat(request.getSourcePayloadFormat());
        existingIntegration.setDestinationPayloadFormat(request.getDestinationPayloadFormat());
        existingIntegration.setHasMiddleware(request.getHasMiddleware());
        existingIntegration.setIsSync(request.getIsSync());

        if (technicalChange) {
            existingIntegration.setStatus(IntegrationStatus.DRAFT);
            if (!existingIntegration.getEnvironmentConfigs().isEmpty() || !existingIntegration.getMappingSchemas().isEmpty()
                    || !existingIntegration.getFieldMappings().isEmpty()) {
                existingIntegration.setReviewRequired(true);
            }
        }

        Integration updatedIntegration = integrationRepository.save(existingIntegration);
        return integrationMapper.toResponse(updatedIntegration);
    }

    private void assignSystems(Integration integration, IntegrationRequestDTO request) {
        java.util.Set<Long> ids = new java.util.TreeSet<>(List.of(request.getSourceSystemId(), request.getDestinationSystemId()));
        if (request.getMiddlewareSystemId() != null) ids.add(request.getMiddlewareSystemId());
        java.util.Map<Long, CatalogSystem> selected = new java.util.HashMap<>();
        for (Long systemId : ids) {
            selected.put(systemId, systemRepository.findByIdForUpdate(systemId)
                    .orElseThrow(() -> new EntityNotFoundException("System not found with id: " + systemId)));
        }
        CatalogSystem source = selected.get(request.getSourceSystemId());
        CatalogSystem destination = selected.get(request.getDestinationSystemId());
        CatalogSystem middleware = selected.get(request.getMiddlewareSystemId());

        boolean creating = integration.getId() == null;
        if ((!source.isActive() && (creating || integration.getSourceSystem() == null || !Objects.equals(integration.getSourceSystem().getId(), source.getId())))
                || (!destination.isActive() && (creating || integration.getDestinationSystem() == null || !Objects.equals(integration.getDestinationSystem().getId(), destination.getId())))
                || (middleware != null && !middleware.isActive() && (creating || integration.getMiddlewareSystem() == null
                || !Objects.equals(integration.getMiddlewareSystem().getId(), middleware.getId())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inactive systems cannot be selected; an existing selection may be retained");
        }

        integration.setSourceSystem(source);
        integration.setDestinationSystem(destination);
        integration.setMiddlewareSystem(middleware);
    }

    @Override
    @Transactional
    public void saveEnvironmentConfigs(Long integrationId, List<EnvironmentConfigRequestDTO> configRequests) {
        Integration integration = integrationRepository.findByIdForUpdate(integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + integrationId));

        List<EnvironmentConfig> oldConfigs = configRepository.findByIntegrationId(integrationId);
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<EnvironmentConfig> newConfigs = configRequests.stream()
                .filter(req -> {
                    if (!integration.getHasMiddleware() && req.getConfigTarget() == ConfigTarget.MIDDLEWARE) {
                        return false;
                    }
                    return true;
                })
                .map(req -> {
                    String configKey = req.getEnvironmentType() + "/" + req.getConfigTarget();
                    if (!seen.add(configKey)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate environment configuration");
                    }
                    EnvironmentConfig old = oldConfigs.stream()
                            .filter(existing -> existing.getEnvironmentType() == req.getEnvironmentType()
                                    && existing.getConfigTarget() == req.getConfigTarget())
                            .findFirst().orElse(null);
                    EnvironmentConfig config = integrationMapper.toConfigEntity(req, integration);
                    if (old != null) {
                        config.setId(old.getId());
                        config.setCreatedAt(old.getCreatedAt());
                    }
                    boolean hadCredentials = old != null && old.getAuthCredentials() != null
                            && !old.getAuthCredentials().isEmpty();
                    if (req.getAuthCredentials() != null || (hadCredentials
                            && (old.getAuthType() != req.getAuthType() || req.getAuthType() == AuthType.NONE))) {
                        credentialAccess.require(UserPermission.MANAGE_CREDENTIALS);
                    }
                    if (req.getAuthType() == AuthType.NONE) {
                        config.setAuthCredentials(null);
                    } else if (req.getAuthCredentials() == null) {
                        if (old != null && old.getAuthType() == req.getAuthType()) {
                            config.setAuthCredentials(old.getAuthCredentials());
                        }
                    } else {
                        if (req.getAuthCredentials().isEmpty()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "Credentials cannot be empty; omit to preserve or select NONE to clear");
                        }
                        config.setAuthCredentials(credentialCrypto.encrypt(req.getAuthCredentials(), config));
                    }
                    if (req.getAuthType() != AuthType.NONE && (config.getAuthCredentials() == null
                            || config.getAuthCredentials().isEmpty())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Credentials are required for the selected authentication type");
                    }
                    return config;
                })
                .collect(Collectors.toList());

        List<EnvironmentConfig> removed = oldConfigs.stream()
                .filter(old -> newConfigs.stream().noneMatch(config ->
                        config.getEnvironmentType() == old.getEnvironmentType()
                                && config.getConfigTarget() == old.getConfigTarget()))
                .toList();
        if (removed.stream().anyMatch(old -> old.getAuthCredentials() != null && !old.getAuthCredentials().isEmpty())) {
            credentialAccess.require(UserPermission.MANAGE_CREDENTIALS);
        }
        configRepository.deleteAll(removed);
        configRepository.saveAll(newConfigs);
        integration.setStatus(IntegrationStatus.DRAFT);
    }

    @Override
    @Transactional
    public void deleteIntegration(Long id) {
        if (!integrationRepository.existsById(id)) {
            throw new EntityNotFoundException("Integration not found with id: " + id);
        }
        integrationRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void completeIntegration(Long id) {
        Integration integration = integrationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + id));
        completionValidator.validate(integration);
        integration.setStatus(IntegrationStatus.COMPLETED);
        integrationRepository.save(integration);
    }

    @Override
    @Transactional
    public IntegrationResponseDTO confirmIntegrationReview(Long id) {
        Integration integration = integrationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + id));
        integration.setReviewRequired(false);
        return integrationMapper.toResponse(integrationRepository.save(integration));
    }
}
