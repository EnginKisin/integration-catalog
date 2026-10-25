package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.destination.DestinationRequestDTO;
import com.company.integration_catalog.dto.destination.DestinationResponseDTO;
import com.company.integration_catalog.model.entity.Destination;
import com.company.integration_catalog.repository.DestinationRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.service.DestinationService;
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
public class DestinationServiceImpl implements DestinationService {
    private final DestinationRepository destinationRepository;
    private final IntegrationRepository integrationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DestinationResponseDTO> getAllDestinations() {
        Map<Long, Long> usage = integrationRepository.destinationUsageCounts().stream()
                .collect(Collectors.toMap(IntegrationRepository.SystemUsage::getSystemId,
                        IntegrationRepository.SystemUsage::getUsageCount));
        return destinationRepository.findAll().stream()
                .map(item -> response(item, usage.getOrDefault(item.getId(), 0L))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DestinationResponseDTO getDestinationById(Long id) {
        Destination item = destinationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Destination not found with id: " + id));
        return response(item, integrationRepository.countByDestinationId(id));
    }

    @Override
    @Transactional
    public DestinationResponseDTO createDestination(DestinationRequestDTO request) {
        Destination item = Destination.builder().name(request.getName()).description(request.getDescription()).build();
        return response(destinationRepository.save(item), 0);
    }

    @Override
    @Transactional
    public DestinationResponseDTO updateDestination(Long id, DestinationRequestDTO request) {
        Destination item = locked(id);
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        return response(destinationRepository.save(item), integrationRepository.countByDestinationId(id));
    }

    @Override
    @Transactional
    public DestinationResponseDTO setDestinationActive(Long id, boolean active) {
        Destination item = locked(id);
        item.setActive(active);
        return response(destinationRepository.save(item), integrationRepository.countByDestinationId(id));
    }

    @Override
    @Transactional
    public void deleteDestination(Long id) {
        Destination item = locked(id);
        long usage = integrationRepository.countByDestinationId(id);
        if (usage > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Destination is used by " + usage + " integration(s); deactivate it instead");
        }
        destinationRepository.delete(item);
    }

    private Destination locked(Long id) {
        return destinationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Destination not found with id: " + id));
    }

    private DestinationResponseDTO response(Destination item, long usageCount) {
        return DestinationResponseDTO.builder().id(item.getId()).name(item.getName())
                .description(item.getDescription()).active(item.isActive()).usageCount(usageCount).build();
    }
}
