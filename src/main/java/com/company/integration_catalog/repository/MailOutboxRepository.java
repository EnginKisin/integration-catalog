package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.MailOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, UUID> {
    long countBySentAtIsNullAndFailedAtIsNull();
    long countByFailedAtIsNotNull();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MailOutbox> findBySentAtIsNullAndFailedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Instant now, Pageable pageable);
}
