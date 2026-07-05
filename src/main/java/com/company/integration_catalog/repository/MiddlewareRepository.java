package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.Middleware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MiddlewareRepository extends JpaRepository<Middleware, Long> {
}