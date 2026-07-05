package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.FieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldMappingRepository extends JpaRepository<FieldMapping, Long> {

    // Bir entegrasyon için çizilmiş tüm mapping çizgilerini getirir (Ekrana çizmek için)
    List<FieldMapping> findByIntegrationId(Long integrationId);

    // Belirli bir entegrasyonda sadece AI'ın yaptığı veya sadece insanın yaptığı eşlemeleri filtreler
    List<FieldMapping> findByIntegrationIdAndIsAiGenerated(Long integrationId, Boolean isAiGenerated);

    // Yeni mapping yapılmadan önce eski çizgileri temizlemek için (Kullanıcı sıfırdan map etmek isterse)
    void deleteByIntegrationId(Long integrationId);
}