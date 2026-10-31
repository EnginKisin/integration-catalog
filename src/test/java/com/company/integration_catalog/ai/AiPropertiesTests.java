package com.company.integration_catalog.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesTests {
    @Test
    void applicationDefaultsLoadWithoutOptionalEnvironmentVariables() {
        runner(Map.of()).withUserConfiguration(AiConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            AiProperties properties = context.getBean(AiProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getProvider()).isEqualTo("ollama");
            assertThat(properties.getBaseUrl()).isEqualTo("http://ollama:11434");
            assertThat(properties.getModel()).isEqualTo("qwen3.5:4b");
            assertThat(properties.getGeminiApiKey()).isEmpty();
            assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(300);
            assertThat(properties.getContextTokens()).isEqualTo(8192);
            assertThat(properties.getMaxOutputTokens()).isEqualTo(2048);
            assertThat(properties.getMaxFields()).isEqualTo(100);
            assertThat(properties.getMaxInputBytes()).isEqualTo(65536);
            assertThat(properties.getMaxPendingJobs()).isEqualTo(10);
            assertThat(properties.getJobTtlHours()).isEqualTo(24);
        });
    }

    @Test
    void environmentValuesOverrideApplicationDefaults() {
        runner(Map.of(
                "AI_ENABLED", "true",
                "AI_PROVIDER", "gemini",
                "AI_BASE_URL", "http://localhost:11434",
                "AI_MODEL", "test-model",
                "GEMINI_API_KEY", "test-key",
                "AI_REQUEST_TIMEOUT_SECONDS", "120",
                "AI_CONTEXT_TOKENS", "4096",
                "AI_MAX_OUTPUT_TOKENS", "1024"
        )).withUserConfiguration(AiConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            AiProperties properties = context.getBean(AiProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getProvider()).isEqualTo("gemini");
            assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:11434");
            assertThat(properties.getModel()).isEqualTo("test-model");
            assertThat(properties.getGeminiApiKey()).isEqualTo("test-key");
            assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(120);
            assertThat(properties.getContextTokens()).isEqualTo(4096);
            assertThat(properties.getMaxOutputTokens()).isEqualTo(1024);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "AI_REQUEST_TIMEOUT_SECONDS,0",
            "AI_REQUEST_TIMEOUT_SECONDS,601",
            "AI_CONTEXT_TOKENS,1024",
            "AI_MAX_OUTPUT_TOKENS,8193",
            "AI_PROVIDER,''",
            "AI_MODEL,''",
            "AI_REQUEST_TIMEOUT_SECONDS,''"
    })
    void invalidEnvironmentValuesStillFailValidation(String variable, String value) {
        runner(Map.of(variable, value)).withUserConfiguration(AiConfiguration.class).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void databasePasswordHasNoBuiltInFallback() {
        runner(Map.of()).withUserConfiguration(DatabaseConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSourceProperties.class).getPassword()).isNull();
        });
    }

    @Test
    void databasePasswordBindsFromEnvironment() {
        runner(Map.of("SPRING_DATASOURCE_PASSWORD", "test-database-password"))
                .withUserConfiguration(DatabaseConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DataSourceProperties.class).getPassword())
                            .isEqualTo("test-database-password");
                });
    }

    private ApplicationContextRunner runner(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
                })
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    static class AiConfiguration {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DataSourceProperties.class)
    static class DatabaseConfiguration {}
}
