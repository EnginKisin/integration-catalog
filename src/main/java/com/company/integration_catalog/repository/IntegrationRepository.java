package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.Integration;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Integration i where i.id = :id")
    Optional<Integration> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"sourceSystem", "destinationSystem", "middlewareSystem", "environmentConfigs"})
    List<Integration> findAll();

    @Override
    @EntityGraph(attributePaths = {"sourceSystem", "destinationSystem", "middlewareSystem", "environmentConfigs"})
    Optional<Integration> findById(Long id);

    @Query("select count(distinct i.id) from Integration i where i.sourceSystem.id = :systemId "
            + "or i.destinationSystem.id = :systemId or i.middlewareSystem.id = :systemId")
    long countBySystemId(@Param("systemId") Long systemId);

    interface SystemUsage {
        Long getSystemId();
        long getUsageCount();
    }

    @Query(value = """
            SELECT system_id AS "systemId", count(*) AS "usageCount"
            FROM (
                SELECT source_system_id AS system_id, id FROM integrations
                UNION
                SELECT destination_system_id AS system_id, id FROM integrations
                UNION
                SELECT middleware_system_id AS system_id, id FROM integrations
                WHERE middleware_system_id IS NOT NULL
            ) usages
            GROUP BY system_id
            """, nativeQuery = true)
    List<SystemUsage> systemUsageCounts();

    @EntityGraph(attributePaths = {"sourceSystem", "destinationSystem", "middlewareSystem", "environmentConfigs"})
    @Query("select distinct i from Integration i left join i.sourceSystem s "
            + "left join i.destinationSystem d left join i.middlewareSystem m "
            + "where lower(i.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(s.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(d.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(m.name) like lower(concat('%', :keyword, '%'))")
    List<Integration> searchIntegrations(@Param("keyword") String keyword);
}
