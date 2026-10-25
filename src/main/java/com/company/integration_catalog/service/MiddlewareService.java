package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.middleware.MiddlewareRequestDTO;
import com.company.integration_catalog.dto.middleware.MiddlewareResponseDTO;
import java.util.List;

public interface MiddlewareService {
    List<MiddlewareResponseDTO> getAllMiddlewares();
    MiddlewareResponseDTO createMiddleware(MiddlewareRequestDTO request);
    MiddlewareResponseDTO getMiddlewareById(Long id);
    MiddlewareResponseDTO updateMiddleware(Long id, MiddlewareRequestDTO request);
    MiddlewareResponseDTO setMiddlewareActive(Long id, boolean active);
    void deleteMiddleware(Long id);
}
