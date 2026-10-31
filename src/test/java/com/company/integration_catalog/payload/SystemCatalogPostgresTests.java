package com.company.integration_catalog.payload;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.system.SystemRequestDTO;
import com.company.integration_catalog.model.entity.CatalogSystem;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "catalog.payload.postgres-test", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${catalog.payload.test-db-url:jdbc:postgresql://localhost:5432/integration_payload_test_20261025}",
        "spring.datasource.username=catalog_payload_test", "spring.datasource.password=payload-test-only-password",
        "catalog.credentials.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "catalog.bootstrap.username=payload-test-admin", "catalog.bootstrap.password=payload-test-admin-password",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional
class SystemCatalogPostgresTests {
    @Autowired SystemRepository repository;
    @Autowired IntegrationRepository integrationRepository;
    @Autowired SystemService systems;
    @Autowired IntegrationService integrations;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void sameSystemCanFillAnyRoleAndCountsEachIntegrationOnlyOnce() {
        Long sap = system("SAP");
        Long crm = system("CRM");
        Long platform = system("Platform");
        var outbound = integrations.createIntegration(request(sap, crm, platform));
        var inbound = integrations.createIntegration(request(crm, sap, null));
        integrations.createIntegration(request(sap, sap, sap));
        em.flush(); em.clear();

        assertThat(outbound.getSourceSystemId()).isEqualTo(inbound.getDestinationSystemId());
        assertThat(outbound.getSourceSystemName()).isEqualTo("SAP");
        assertThat(inbound.getDestinationSystemName()).isEqualTo("SAP");
        assertThat(integrationRepository.countBySystemId(sap)).isEqualTo(3);
        assertThat(integrationRepository.countBySystemId(crm)).isEqualTo(2);
        assertThat(integrationRepository.countBySystemId(platform)).isEqualTo(1);
        var catalog = systems.getAllSystems();
        assertThat(catalog.stream().filter(item -> item.getId().equals(sap)).findFirst().orElseThrow().getUsageCount()).isEqualTo(3);
        assertThat(catalog.stream().filter(item -> item.getId().equals(crm)).findFirst().orElseThrow().getUsageCount()).isEqualTo(2);
        assertThat(systems.getSystemById(platform).getUsageCount()).isEqualTo(1);
        assertThat(integrations.getAllIntegrations("Platform")).extracting(item -> item.getId())
                .containsExactly(outbound.getId());
    }

    @Test
    void listAndDetailExposeSharedNamesAndDistinctConfiguredEnvironments() {
        Long source = system("SAP");
        Long destination = system("CRM");
        Long middleware = system("Platform");
        Long id = integrations.createIntegration(request(source, destination, middleware)).getId();
        integrations.saveEnvironmentConfigs(id, List.of(
                config(EnvironmentType.DEV, ConfigTarget.DESTINATION),
                config(EnvironmentType.DEV, ConfigTarget.MIDDLEWARE),
                config(EnvironmentType.PROD, ConfigTarget.DESTINATION),
                config(EnvironmentType.PROD, ConfigTarget.MIDDLEWARE)));
        systems.updateSystem(source, new SystemRequestDTO("SAP ERP", "Renamed shared definition"));
        em.flush(); em.clear();

        var detail = integrations.getIntegrationById(id);
        var listed = integrations.getAllIntegrations(null).stream().filter(row -> row.getId().equals(id)).findFirst().orElseThrow();
        for (var response : List.of(detail, listed)) {
            assertThat(response.getSourceSystemId()).isEqualTo(source);
            assertThat(response.getSourceSystemName()).isEqualTo("SAP ERP");
            assertThat(response.getDestinationSystemName()).isEqualTo("CRM");
            assertThat(response.getMiddlewareSystemName()).isEqualTo("Platform");
            assertThat(response.getEnvironmentTypes()).containsExactly(EnvironmentType.DEV, EnvironmentType.PROD);
            assertThat(response.getEnvironmentConfigs()).hasSize(4);
        }
        assertThat(detail.getEnvironmentConfigs()).extracting(row -> row.getConfigTarget())
                .contains(ConfigTarget.DESTINATION, ConfigTarget.MIDDLEWARE);
    }

    @Test
    void middlewareUsageAlsoPreventsDeletingAnInactiveSystem() {
        Long source = system("SAP"), destination = system("CRM"), middleware = system("Platform");
        integrations.createIntegration(request(source, destination, middleware));
        systems.setSystemActive(middleware, false);
        assertThatThrownBy(() -> systems.deleteSystem(middleware))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
        assertThat(repository.findById(middleware)).isPresent();
    }

    @Test
    void inactiveSystemsRemainVisibleAndCanBeRetainedInTheirExistingRole() {
        Long source = system("SAP"), destination = system("CRM");
        Long id = integrations.createIntegration(request(source, destination, null)).getId();
        systems.setSystemActive(source, false);
        var update = request(source, destination, null);
        update.setName("Updated description");
        integrations.updateIntegration(id, update);
        em.flush(); em.clear();
        assertThat(integrations.getIntegrationById(id).getSourceSystemName()).isEqualTo("SAP");
        assertThat(systems.getSystemById(source).isActive()).isFalse();
        assertThatThrownBy(() -> integrations.createIntegration(request(destination, source, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void unusedSystemCanBeDeletedAndNewSchemaHasNoLegacyCatalogTables() {
        Long unused = system("Unused");
        systems.deleteSystem(unused);
        em.flush();
        assertThat(repository.findById(unused)).isEmpty();
        assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema='public' "
                + "and table_name in ('sources','destinations','middlewares')", String.class)).isEmpty();
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' "
                + "and table_name='integrations' and column_name in ('source_id','destination_id','middleware_id')", String.class)).isEmpty();
    }

    private Long system(String name) {
        return systems.createSystem(new SystemRequestDTO(name, "System test")).getId();
    }

    private IntegrationRequestDTO request(Long source, Long destination, Long middleware) {
        return IntegrationRequestDTO.builder().name("System integration").description("")
                .sourceSystemId(source).destinationSystemId(destination).middlewareSystemId(middleware)
                .hasMiddleware(middleware != null).isSync(false)
                .sourcePayloadFormat(PayloadFormat.JSON).destinationPayloadFormat(PayloadFormat.JSON).build();
    }

    private EnvironmentConfigRequestDTO config(EnvironmentType environment, ConfigTarget target) {
        return EnvironmentConfigRequestDTO.builder().environmentType(environment).configTarget(target)
                .url("https://example.com").authType(AuthType.NONE).build();
    }
}
