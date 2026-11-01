package com.company.integration_catalog.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "catalog.payload.postgres-test", matches = "true")
class FlywayMigrationPostgresTests {
    private Connection connection;
    private String schema;
    private Flyway flyway;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        String url = System.getProperty("catalog.payload.test-db-url",
                "jdbc:postgresql://localhost:5432/integration_payload_test_20261025");
        connection = DriverManager.getConnection(url, "catalog_payload_test", "payload-test-only-password");
        schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        flyway = Flyway.configure()
                .dataSource(url, "catalog_payload_test", "payload-test-only-password")
                .schemas(schema)
                .defaultSchema(schema)
                .load();
    }

    @AfterEach
    void removeIsolatedSchema() throws Exception {
        if (connection == null) return;
        try (var testConnection = connection; var statement = testConnection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void migratesAnEmptySchemaAndDoesNotReplayMigrationsOnRestart() throws Exception {
        assertThat(flyway.migrate().migrationsExecuted).isPositive();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();

        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO " + schema
                    + ".systems (name, description) VALUES ('Migration test', 'Survives restart')");
        }

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT name FROM " + schema + ".systems")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("name")).isEqualTo("Migration test");
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void upgradesVersionOneWithoutRecreatingExistingTables() throws Exception {
        Flyway versionOne = Flyway.configure().configuration(flyway.getConfiguration()).target("1").load();
        assertThat(versionOne.migrate().migrationsExecuted).isEqualTo(1);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO " + schema
                    + ".systems (name, description) VALUES ('Before upgrade', 'Retained by V2')");
        }

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT name FROM " + schema + ".systems")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("name")).isEqualTo("Before upgrade");
        }
        try (var statement = connection.createStatement()) {
            statement.executeQuery("SELECT * FROM " + schema + ".spring_session").close();
            statement.executeQuery("SELECT * FROM " + schema + ".spring_session_attributes").close();
        }
    }

    @Test
    void refusesAnUnmanagedNonemptySchemaInsteadOfSkippingTheInitialMigration() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + schema + ".legacy_table (id BIGINT PRIMARY KEY)");
        }

        assertThatThrownBy(() -> flyway.migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");
    }
}
