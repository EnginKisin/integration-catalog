package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.LookUpDTO;
import com.company.integration_catalog.service.MiddlewareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/middlewares")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MiddlewareController {

    private final MiddlewareService middlewareService;

    @GetMapping
    public ResponseEntity<List<LookUpDTO.Response>> getAllMiddlewares() {
        return ResponseEntity.ok(middlewareService.getAllMiddlewares());
    }

    @PostMapping
    public ResponseEntity<LookUpDTO.Response> createMiddleware(@RequestBody LookUpDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(middlewareService.createMiddleware(request));
    }
}