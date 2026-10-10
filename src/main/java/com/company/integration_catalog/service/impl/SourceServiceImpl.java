package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.source.SourceRequestDTO;
import com.company.integration_catalog.dto.source.SourceResponseDTO;
import com.company.integration_catalog.model.entity.Source;
import com.company.integration_catalog.repository.SourceRepository;
import com.company.integration_catalog.service.SourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourceServiceImpl implements SourceService {

    private final SourceRepository sourceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SourceResponseDTO> getAllSources() {
        return sourceRepository.findAll().stream()
                .map(source -> SourceResponseDTO.builder()
                        .id(source.getId())
                        .name(source.getName())
                        .description(source.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SourceResponseDTO createSource(SourceRequestDTO request) {
        Source source = Source.builder().name(request.getName()).description(request.getDescription()).build();
        Source saved = sourceRepository.save(source);
        return SourceResponseDTO.builder().id(saved.getId()).name(saved.getName()).description(saved.getDescription()).build();
    }
}