package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.destination.DestinationRequestDTO;
import com.company.integration_catalog.dto.destination.DestinationResponseDTO;
import com.company.integration_catalog.model.entity.Destination;
import com.company.integration_catalog.repository.DestinationRepository;
import com.company.integration_catalog.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private final DestinationRepository destinationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DestinationResponseDTO> getAllDestinations() {
        return destinationRepository.findAll().stream()
                .map(source -> DestinationResponseDTO.builder()
                        .id(source.getId())
                        .name(source.getName())
                        .description(source.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DestinationResponseDTO createDestination(DestinationRequestDTO request) {
        Destination source = Destination.builder().name(request.getName()).description(request.getDescription()).build();
        Destination saved = destinationRepository.save(source);
        return DestinationResponseDTO.builder().id(saved.getId()).name(saved.getName()).description(saved.getDescription()).build();
    }
}