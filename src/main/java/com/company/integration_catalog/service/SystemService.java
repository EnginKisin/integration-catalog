package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.system.SystemRequestDTO;
import com.company.integration_catalog.dto.system.SystemResponseDTO;
import java.util.List;

public interface SystemService {
    List<SystemResponseDTO> getAllSystems();
    SystemResponseDTO createSystem(SystemRequestDTO request);
    SystemResponseDTO getSystemById(Long id);
    SystemResponseDTO updateSystem(Long id, SystemRequestDTO request);
    SystemResponseDTO setSystemActive(Long id, boolean active);
    void deleteSystem(Long id);
}
