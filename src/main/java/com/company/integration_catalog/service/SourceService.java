package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.LookUpDTO;
import java.util.List;

public interface SourceService {
    List<LookUpDTO.Response> getAllSources();
    LookUpDTO.Response createSource(LookUpDTO.Request request);
}