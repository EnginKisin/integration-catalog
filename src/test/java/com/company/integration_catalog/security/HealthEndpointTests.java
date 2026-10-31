package com.company.integration_catalog.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = HealthEndpointTests.TestApplication.class)
@AutoConfigureMockMvc
class HealthEndpointTests {
    @Autowired private MockMvc mvc;
    @Autowired private ConfigurableApplicationContext context;
    @Autowired private AtomicBoolean databaseAvailable;
    @MockitoBean private CredentialAccess credentialAccess;
    @MockitoBean private com.company.integration_catalog.repository.UserAccountRepository accounts;

    @BeforeEach
    void ready() {
        databaseAvailable.set(true);
        AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void anonymousProbesWorkWithoutExposingDetails(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void databaseFailureStopsReadinessButKeepsLivenessUp() throws Exception {
        databaseAvailable.set(false);
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components").doesNotExist());
        databaseAvailable.set(true);
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void readinessTracksApplicationAvailability() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void livenessTracksBrokenApplication() throws Exception {
        AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isServiceUnavailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/metrics", "/actuator/health/db"})
    void otherActuatorPathsRemainPrivate(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void writesToHealthAreNotPublic() throws Exception {
        mvc.perform(post("/actuator/health").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import(SecurityConfig.class)
    static class TestApplication {
        @Bean AtomicBoolean databaseAvailable() { return new AtomicBoolean(true); }

        @Bean HealthIndicator dbHealthIndicator(AtomicBoolean available) {
            return () -> (available.get() ? Health.up() : Health.down())
                    .withDetail("database", "private-database-info").build();
        }
        @Bean UserDetailsService userDetailsService() { return new InMemoryUserDetailsManager(); }
    }
}
