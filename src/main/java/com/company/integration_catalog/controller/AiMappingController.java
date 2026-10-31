package com.company.integration_catalog.controller;

import com.company.integration_catalog.ai.AiMappingDtos.*;
import com.company.integration_catalog.ai.AiMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/integrations/{integrationId}/mappings") @RequiredArgsConstructor
public class AiMappingController {
    private final AiMappingService service;
    @GetMapping("/context") public Context context(@PathVariable Long integrationId) { return service.context(integrationId); }
    @GetMapping("/ai/capabilities") public Capabilities capabilities(@PathVariable Long integrationId) {
        service.context(integrationId); // Scope availability to an existing integration.
        return service.capabilities();
    }
    @PostMapping("/ai/jobs")
    public ResponseEntity<JobView> create(@PathVariable Long integrationId, Principal principal,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateJob request) {
        JobView job=service.create(integrationId,principal.getName(),key,request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/integrations/"+integrationId+"/mappings/ai/jobs/"+job.jobId())).body(job);
    }
    @GetMapping("/ai/jobs/{jobId}") public JobView status(@PathVariable Long integrationId,@PathVariable String jobId) {
        return service.status(integrationId,jobId);
    }
    @GetMapping("/ai/jobs/{jobId}/result") public Result result(@PathVariable Long integrationId,@PathVariable String jobId) {
        return service.result(integrationId,jobId);
    }
    @PostMapping("/fields/append")
    public AppendResponse append(@PathVariable Long integrationId,Principal principal,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AppendRequest request) {
        return service.append(integrationId,principal.getName(),key,request);
    }
}
