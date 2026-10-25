package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.source.SourceRequestDTO;
import com.company.integration_catalog.dto.source.SourceResponseDTO;
import java.util.List;

public interface SourceService {
    List<SourceResponseDTO> getAllSources();
    SourceResponseDTO createSource(SourceRequestDTO request);
    SourceResponseDTO getSourceById(Long id);
    SourceResponseDTO updateSource(Long id, SourceRequestDTO request);
    SourceResponseDTO setSourceActive(Long id, boolean active);
    void deleteSource(Long id);
}
