package com.company.integration_catalog.ai;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface AiJobRepository extends JpaRepository<AiJob,String> {
    Optional<AiJob> findByIdAndIntegrationId(String id, Long integrationId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiJob j where j.id=:id")
    Optional<AiJob> findLockedById(@Param("id") String id);
    Optional<AiJob> findByIntegrationIdAndCreatedByAndIdempotencyKey(Long id, String user, String key);
    long countByStatusIn(java.util.Collection<String> statuses);

    @Query(value="SELECT * FROM ai_mapping_jobs WHERE status='QUEUED' AND expires_at > :now "
            + "ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery=true)
    Optional<AiJob> claimNext(@Param("now") Instant now);

    @Modifying @Query("update AiJob j set j.status='EXPIRED', j.inputSnapshot=null, j.result=null "
            + "where j.status <> 'EXPIRED' and j.expiresAt <= :now")
    int expire(@Param("now") Instant now);

    @Modifying @Query("update AiJob j set j.status='FAILED', j.errorCode='AI_INTERRUPTED', "
            + "j.errorMessage='Worker interrupted or execution lease expired', j.inputSnapshot=null "
            + "where j.status='RUNNING' and j.leaseUntil < :now")
    int failInterrupted(@Param("now") Instant now);
}
