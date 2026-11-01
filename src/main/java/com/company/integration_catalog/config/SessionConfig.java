package com.company.integration_catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

@Configuration(proxyBeanMethods = false)
public class SessionConfig {
    @Bean
    PostgreSqlJdbcIndexedSessionRepositoryCustomizer postgresSessionQueries() {
        // Use PostgreSQL upserts when concurrent requests create the same attribute.
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }
}
