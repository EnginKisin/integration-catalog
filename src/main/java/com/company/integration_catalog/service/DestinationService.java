package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.LookUpDTO;
import java.util.List;

public interface DestinationService {
    List<LookUpDTO.Response> getAllDestinations();
    LookUpDTO.Response createDestination(LookUpDTO.Request request);
}