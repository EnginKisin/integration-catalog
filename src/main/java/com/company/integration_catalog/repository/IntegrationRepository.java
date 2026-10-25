package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Integration i where i.id = :id")
    java.util.Optional<Integration> findByIdForUpdate(@Param("id") Long id);

    long countBySourceId(Long sourceId);
    long countByDestinationId(Long destinationId);
    long countByMiddlewareId(Long middlewareId);

    interface SystemUsage {
        Long getSystemId();
        long getUsageCount();
    }

    @Query("select i.source.id as systemId, count(i) as usageCount from Integration i group by i.source.id")
    List<SystemUsage> sourceUsageCounts();

    @Query("select i.destination.id as systemId, count(i) as usageCount from Integration i group by i.destination.id")
    List<SystemUsage> destinationUsageCounts();

    @Query("select i.middleware.id as systemId, count(i) as usageCount from Integration i where i.middleware is not null group by i.middleware.id")
    List<SystemUsage> middlewareUsageCounts();

    // Dashboard'daki arama çubuğu için: İsim, kaynak veya hedef sisteme göre filtreleme
    @Query("SELECT i FROM Integration i " +
           "LEFT JOIN i.source s " +
           "LEFT JOIN i.destination d " +
           "WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Integration> searchIntegrations(@Param("keyword") String keyword);

    // Middleware kullanan entegrasyonları listelemek için (Adım 3 optimizasyonları için gerekebilir)
    List<Integration> findByHasMiddlewareTrue();
}
