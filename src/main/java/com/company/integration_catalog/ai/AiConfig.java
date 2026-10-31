package com.company.integration_catalog.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration @EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService aiMappingExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("ai-mapping-worker").factory());
    }
}
