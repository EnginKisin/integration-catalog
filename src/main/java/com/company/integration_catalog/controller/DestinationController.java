package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.LookUpDTO;
import com.company.integration_catalog.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping
    public ResponseEntity<List<LookUpDTO.Response>> getAllDestinations() {
        return ResponseEntity.ok(destinationService.getAllDestinations());
    }

    @PostMapping
    public ResponseEntity<LookUpDTO.Response> createDestination(@RequestBody LookUpDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(destinationService.createDestination(request));
    }
}