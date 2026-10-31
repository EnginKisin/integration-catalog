package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.system.SystemRequestDTO;
import com.company.integration_catalog.dto.system.SystemResponseDTO;
import com.company.integration_catalog.model.entity.CatalogSystem;
import com.company.integration_catalog.repository.SystemRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.service.SystemService;
import com.company.integration_catalog.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.company.integration_catalog.exception.ApiException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemServiceImpl implements SystemService {
    private final SystemRepository systemRepository;
    private final IntegrationRepository integrationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SystemResponseDTO> getAllSystems() {
        Map<Long, Long> usage = integrationRepository.systemUsageCounts().stream()
                .collect(Collectors.toMap(IntegrationRepository.SystemUsage::getSystemId,
                        IntegrationRepository.SystemUsage::getUsageCount));
        return systemRepository.findAll().stream()
                .map(item -> response(item, usage.getOrDefault(item.getId(), 0L))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemResponseDTO getSystemById(Long id) {
        CatalogSystem item = systemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "System not found with id: " + id));
        return response(item, integrationRepository.countBySystemId(id));
    }

    @Override
    @Transactional
    public SystemResponseDTO createSystem(SystemRequestDTO request) {
        CatalogSystem item = CatalogSystem.builder().name(request.getName()).description(request.getDescription()).build();
        return response(systemRepository.save(item), 0);
    }

    @Override
    @Transactional
    public SystemResponseDTO updateSystem(Long id, SystemRequestDTO request) {
        CatalogSystem item = locked(id);
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        return response(systemRepository.save(item), integrationRepository.countBySystemId(id));
    }

    @Override
    @Transactional
    public SystemResponseDTO setSystemActive(Long id, boolean active) {
        CatalogSystem item = locked(id);
        item.setActive(active);
        return response(systemRepository.save(item), integrationRepository.countBySystemId(id));
    }

    @Override
    @Transactional
    public void deleteSystem(Long id) {
        CatalogSystem item = locked(id);
        long usage = integrationRepository.countBySystemId(id);
        if (usage > 0) {
            throw new ApiException(409, "SYSTEM_IN_USE",
                    "System is used by " + usage + " integration(s); deactivate it instead",
                    Map.of("usageCount", usage), List.of(), null);
        }
        systemRepository.delete(item);
    }

    private CatalogSystem locked(Long id) {
        return systemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("SYSTEM_NOT_FOUND", "System not found with id: " + id));
    }

    private SystemResponseDTO response(CatalogSystem item, long usageCount) {
        return SystemResponseDTO.builder().id(item.getId()).name(item.getName())
                .description(item.getDescription()).active(item.isActive()).usageCount(usageCount).build();
    }
}
