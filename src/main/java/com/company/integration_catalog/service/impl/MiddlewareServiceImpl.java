package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.middleware.MiddlewareRequestDTO;
import com.company.integration_catalog.dto.middleware.MiddlewareResponseDTO;
import com.company.integration_catalog.model.entity.Middleware;
import com.company.integration_catalog.repository.MiddlewareRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.service.MiddlewareService;
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
public class MiddlewareServiceImpl implements MiddlewareService {
    private final MiddlewareRepository middlewareRepository;
    private final IntegrationRepository integrationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MiddlewareResponseDTO> getAllMiddlewares() {
        Map<Long, Long> usage = integrationRepository.middlewareUsageCounts().stream()
                .collect(Collectors.toMap(IntegrationRepository.SystemUsage::getSystemId,
                        IntegrationRepository.SystemUsage::getUsageCount));
        return middlewareRepository.findAll().stream()
                .map(item -> response(item, usage.getOrDefault(item.getId(), 0L))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MiddlewareResponseDTO getMiddlewareById(Long id) {
        Middleware item = middlewareRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Middleware not found with id: " + id));
        return response(item, integrationRepository.countByMiddlewareId(id));
    }

    @Override
    @Transactional
    public MiddlewareResponseDTO createMiddleware(MiddlewareRequestDTO request) {
        Middleware item = Middleware.builder().name(request.getName()).description(request.getDescription()).build();
        return response(middlewareRepository.save(item), 0);
    }

    @Override
    @Transactional
    public MiddlewareResponseDTO updateMiddleware(Long id, MiddlewareRequestDTO request) {
        Middleware item = locked(id);
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        return response(middlewareRepository.save(item), integrationRepository.countByMiddlewareId(id));
    }

    @Override
    @Transactional
    public MiddlewareResponseDTO setMiddlewareActive(Long id, boolean active) {
        Middleware item = locked(id);
        item.setActive(active);
        return response(middlewareRepository.save(item), integrationRepository.countByMiddlewareId(id));
    }

    @Override
    @Transactional
    public void deleteMiddleware(Long id) {
        Middleware item = locked(id);
        long usage = integrationRepository.countByMiddlewareId(id);
        if (usage > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Middleware is used by " + usage + " integration(s); deactivate it instead");
        }
        middlewareRepository.delete(item);
    }

    private Middleware locked(Long id) {
        return middlewareRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Middleware not found with id: " + id));
    }

    private MiddlewareResponseDTO response(Middleware item, long usageCount) {
        return MiddlewareResponseDTO.builder().id(item.getId()).name(item.getName())
                .description(item.getDescription()).active(item.isActive()).usageCount(usageCount).build();
    }
}
