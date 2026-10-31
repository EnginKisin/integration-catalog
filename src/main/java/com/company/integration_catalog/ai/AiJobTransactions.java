package com.company.integration_catalog.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Map;

@Service @RequiredArgsConstructor
public class AiJobTransactions {
    private final AiJobRepository jobs;
    private final AiProperties properties;
    private final Clock clock;
    private final ObjectMapper json;

    public record Claimed(String id,String provider,String model,AiMappingDtos.Input input) {}

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${catalog.ai.cleanup-ms:60000}")
    @Transactional
    public void cleanup() {

        jobs.expire(clock.instant());
        jobs.failInterrupted(clock.instant());
    }

    @Transactional
    public Claimed claim() {
        jobs.expire(clock.instant());
        jobs.failInterrupted(clock.instant());
        return jobs.claimNext(clock.instant()).map(job -> {
            job.setStatus("RUNNING");
            job.setLeaseUntil(clock.instant().plusSeconds(properties.getRequestTimeoutSeconds()+60L));
            return new Claimed(job.getId(),job.getProvider(),job.getModel(),
                    json.convertValue(job.getInputSnapshot(),AiMappingDtos.Input.class));
        }).orElse(null);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void finish(String id,AiMappingDtos.Suggestions result,String code,String message) {
        jobs.findLockedById(id).ifPresent(job -> {
            if (!"RUNNING".equals(job.getStatus()) || job.getExpiresAt().compareTo(clock.instant())<=0) return;
            job.setStatus(code==null?"SUCCEEDED":"FAILED");
            job.setResult(result==null?null:json.convertValue(result,Map.class));
            job.setErrorCode(code); job.setErrorMessage(message);
            job.setInputSnapshot(null); job.setLeaseUntil(null);
        });
    }
}
