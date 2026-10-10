package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.destination.DestinationRequestDTO;
import com.company.integration_catalog.dto.destination.DestinationResponseDTO;
import java.util.List;

public interface DestinationService {
    List<DestinationResponseDTO> getAllDestinations();
    DestinationResponseDTO createDestination(DestinationRequestDTO request);
}