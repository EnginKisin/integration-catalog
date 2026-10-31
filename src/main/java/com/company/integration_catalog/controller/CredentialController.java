package com.company.integration_catalog.controller;

import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;
import com.company.integration_catalog.service.CredentialService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/{integrationId}/configs/{environment}/{target}/credentials")
@RequiredArgsConstructor
public class CredentialController {
    private final CredentialService service;

    @GetMapping
    public ResponseEntity<CredentialResponse> reveal(@PathVariable Long integrationId,
            @PathVariable EnvironmentType environment, @PathVariable ConfigTarget target) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CredentialResponse(service.reveal(integrationId, environment, target)));
    }

    @Getter
    @RequiredArgsConstructor
    public static class CredentialResponse {
        private final Map<String, Object> authCredentials;
    }
}
