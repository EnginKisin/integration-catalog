package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.LookUpDTO;
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
    public List<LookUpDTO.Response> getAllSources() {
        return sourceRepository.findAll().stream()
                .map(source -> LookUpDTO.Response.builder()
                        .id(source.getId())
                        .name(source.getName())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LookUpDTO.Response createSource(LookUpDTO.Request request) {
        Source source = Source.builder().name(request.getName()).build();
        Source saved = sourceRepository.save(source);
        return LookUpDTO.Response.builder().id(saved.getId()).name(saved.getName()).build();
    }
}