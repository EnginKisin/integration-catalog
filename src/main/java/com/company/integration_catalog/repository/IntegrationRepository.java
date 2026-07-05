package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, Long> {

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