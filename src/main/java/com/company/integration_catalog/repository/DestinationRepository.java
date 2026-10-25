package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select item from Destination item where item.id = :id")
    java.util.Optional<Destination> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
