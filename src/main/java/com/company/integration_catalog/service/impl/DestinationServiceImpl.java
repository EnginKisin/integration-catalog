package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.LookUpDTO;
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
    public List<LookUpDTO.Response> getAllDestinations() {
        return destinationRepository.findAll().stream()
                .map(source -> LookUpDTO.Response.builder()
                        .id(source.getId())
                        .name(source.getName())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LookUpDTO.Response createDestination(LookUpDTO.Request request) {
        Destination source = Destination.builder().name(request.getName()).build();
        Destination saved = destinationRepository.save(source);
        return LookUpDTO.Response.builder().id(saved.getId()).name(saved.getName()).build();
    }
}