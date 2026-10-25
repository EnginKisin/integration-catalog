package com.company.integration_catalog.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
public class AccountWorkflowConfig {
    @Bean public Clock accountClock() { return Clock.systemUTC(); }
}
