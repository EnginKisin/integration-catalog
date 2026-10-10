package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.middleware.MiddlewareRequestDTO;
import com.company.integration_catalog.dto.middleware.MiddlewareResponseDTO;
import com.company.integration_catalog.model.entity.Middleware;
import com.company.integration_catalog.repository.MiddlewareRepository;
import com.company.integration_catalog.service.MiddlewareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MiddlewareServiceImpl implements MiddlewareService {

    private final MiddlewareRepository middlewareRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MiddlewareResponseDTO> getAllMiddlewares() {
        return middlewareRepository.findAll().stream()
                .map(source -> MiddlewareResponseDTO.builder()
                        .id(source.getId())
                        .name(source.getName())
                        .description(source.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MiddlewareResponseDTO createMiddleware(MiddlewareRequestDTO request) {
        Middleware source = Middleware.builder().name(request.getName()).description(request.getDescription()).build();
        Middleware saved = middlewareRepository.save(source);
        return MiddlewareResponseDTO.builder().id(saved.getId()).name(saved.getName()).description(saved.getDescription()).build();
    }
}