package com.company.integration_catalog.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MappingAppendReceiptRepository extends JpaRepository<MappingAppendReceipt,String> {
    Optional<MappingAppendReceipt> findByIntegrationIdAndCreatedByAndIdempotencyKey(Long id, String user, String key);
}
