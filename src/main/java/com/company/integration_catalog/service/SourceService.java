package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.source.SourceRequestDTO;
import com.company.integration_catalog.dto.source.SourceResponseDTO;
import java.util.List;

public interface SourceService {
    List<SourceResponseDTO> getAllSources();
    SourceResponseDTO createSource(SourceRequestDTO request);
}