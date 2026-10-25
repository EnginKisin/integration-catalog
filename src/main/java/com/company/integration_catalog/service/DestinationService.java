package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.destination.DestinationRequestDTO;
import com.company.integration_catalog.dto.destination.DestinationResponseDTO;
import java.util.List;

public interface DestinationService {
    List<DestinationResponseDTO> getAllDestinations();
    DestinationResponseDTO createDestination(DestinationRequestDTO request);
    DestinationResponseDTO getDestinationById(Long id);
    DestinationResponseDTO updateDestination(Long id, DestinationRequestDTO request);
    DestinationResponseDTO setDestinationActive(Long id, boolean active);
    void deleteDestination(Long id);
}
