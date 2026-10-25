package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.source.SourceRequestDTO;
import com.company.integration_catalog.dto.source.SourceResponseDTO;
import com.company.integration_catalog.model.entity.Source;
import com.company.integration_catalog.repository.SourceRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.service.SourceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourceServiceImpl implements SourceService {
    private final SourceRepository sourceRepository;
    private final IntegrationRepository integrationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SourceResponseDTO> getAllSources() {
        Map<Long, Long> usage = integrationRepository.sourceUsageCounts().stream()
                .collect(Collectors.toMap(IntegrationRepository.SystemUsage::getSystemId,
                        IntegrationRepository.SystemUsage::getUsageCount));
        return sourceRepository.findAll().stream()
                .map(item -> response(item, usage.getOrDefault(item.getId(), 0L))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SourceResponseDTO getSourceById(Long id) {
        Source item = sourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Source not found with id: " + id));
        return response(item, integrationRepository.countBySourceId(id));
    }

    @Override
    @Transactional
    public SourceResponseDTO createSource(SourceRequestDTO request) {
        Source item = Source.builder().name(request.getName()).description(request.getDescription()).build();
        return response(sourceRepository.save(item), 0);
    }

    @Override
    @Transactional
    public SourceResponseDTO updateSource(Long id, SourceRequestDTO request) {
        Source item = locked(id);
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        return response(sourceRepository.save(item), integrationRepository.countBySourceId(id));
    }

    @Override
    @Transactional
    public SourceResponseDTO setSourceActive(Long id, boolean active) {
        Source item = locked(id);
        item.setActive(active);
        return response(sourceRepository.save(item), integrationRepository.countBySourceId(id));
    }

    @Override
    @Transactional
    public void deleteSource(Long id) {
        Source item = locked(id);
        long usage = integrationRepository.countBySourceId(id);
        if (usage > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Source is used by " + usage + " integration(s); deactivate it instead");
        }
        sourceRepository.delete(item);
    }

    private Source locked(Long id) {
        return sourceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Source not found with id: " + id));
    }

    private SourceResponseDTO response(Source item, long usageCount) {
        return SourceResponseDTO.builder().id(item.getId()).name(item.getName())
                .description(item.getDescription()).active(item.isActive()).usageCount(usageCount).build();
    }
}
