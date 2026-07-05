package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.LookUpDTO;
import java.util.List;

public interface MiddlewareService {
    List<LookUpDTO.Response> getAllMiddlewares();
    LookUpDTO.Response createMiddleware(LookUpDTO.Request request);
}