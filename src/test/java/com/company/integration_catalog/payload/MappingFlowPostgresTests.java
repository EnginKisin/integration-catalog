package com.company.integration_catalog.payload;

import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.MappingService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@EnabledIfSystemProperty(named = "catalog.payload.postgres-test", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${catalog.payload.test-db-url:jdbc:postgresql://localhost:5432/integration_payload_test_20261025}",
        "spring.datasource.username=catalog_payload_test", "spring.datasource.password=payload-test-only-password",
        "catalog.credentials.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "catalog.bootstrap.username=payload-test-admin", "catalog.bootstrap.password=payload-test-admin-password",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
class MappingFlowPostgresTests {
    @Autowired SystemRepository systems;
    @Autowired IntegrationRepository integrations;
    @Autowired MappingSchemaRepository schemas;
    @Autowired FieldMappingRepository fields;
    @Autowired MappingService mappings;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean IntegrationMapper mapper;

    private static final Map<String, Object> DEFINITION = Map.of("type", "object");
    private static final byte[] SAMPLE = "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest @EnumSource(SchemaType.class)
    void firstCurrentFlowSampleRemovesObsoleteSchemasAndLinks(SchemaType type) {
        Fixture fixture = fixture(type.isCompatibleWithFlow(true));
        try {
            MappingSchemaRequestDTO request = request(type);
            MappingSchemaResponseDTO saved;
            if (type.name().endsWith("RESPONSE")) {
                request.setSchemaDefinition(null);
                saved = mappings.uploadSchema(fixture.id(), request,
                        new MockMultipartFile("file", "sample.json", "application/json", SAMPLE));
            } else {
                saved = mappings.saveSchema(fixture.id(), request);
            }

            assertThat(mappings.getSchemasByIntegration(fixture.id())).singleElement().satisfies(schema -> {
                assertThat(schema.getId()).isEqualTo(saved.getId());
                assertThat(schema.getSchemaType()).isEqualTo(type);
                assertThat(schema.getSchemaDefinition()).isEqualTo(DEFINITION);
            });
            assertThat(mappings.getFieldMappingsByIntegration(fixture.id())).isEmpty();
            assertThat(schemas.findAllById(fixture.oldSchemaIds())).isEmpty();
            assertThat(fields.findAllById(fixture.oldMappingIds())).isEmpty();
            assertThatThrownBy(() -> mappings.getSampleFile(fixture.id(), fixture.oldSchemaIds().getFirst()))
                    .isInstanceOf(EntityNotFoundException.class);
            assertThat(mappings.getSchemasByIntegration(fixture.neighborId())).hasSize(1);
            assertThat(mappings.getFieldMappingsByIntegration(fixture.neighborId())).hasSize(1);
            assertThat(integrations.findById(fixture.id()).orElseThrow()).satisfies(integration -> {
                assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
                assertThat(integration.isReviewRequired()).isTrue();
            });
        } finally {
            cleanup(fixture);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void upsertPreservesCurrentSchemasAndLinksWhileRemovingLinksToObsoleteSchemas(boolean middleware) {
        Fixture fixture = fixture(middleware);
        try {
            List<Long> retainedIds = new TransactionTemplate(transactions).execute(status -> {
                Integration integration = integrations.findByIdForUpdate(fixture.id()).orElseThrow();
                MappingSchema current = sample(integration, firstType(middleware));
                MappingSchema target = sample(integration,
                        middleware ? SchemaType.MW_TO_DEST_REQUEST : SchemaType.DEST_TO_SOURCE_RESPONSE);
                FieldMapping retained = fields.save(FieldMapping.builder().integration(integration)
                        .sourceSchema(current).targetSchema(target).sourceFieldPath("id").targetFieldPath("id")
                        .isAiGenerated(false).build());
                MappingSchema obsolete = schemas.findById(fixture.oldSchemaIds().getFirst()).orElseThrow();
                fields.save(FieldMapping.builder().integration(integration).sourceSchema(current).targetSchema(obsolete)
                        .sourceFieldPath("id").targetFieldPath("id").isAiGenerated(false).build());
                fields.save(FieldMapping.builder().integration(integration).sourceSchema(obsolete).targetSchema(current)
                        .sourceFieldPath("id").targetFieldPath("id").isAiGenerated(false).build());
                return List.of(current.getId(), target.getId(), retained.getId());
            });

            assertThat(mappings.saveSchema(fixture.id(), request(firstType(middleware))).getId())
                    .isEqualTo(retainedIds.get(0));
            assertThat(mappings.getSchemasByIntegration(fixture.id())).extracting(MappingSchemaResponseDTO::getId)
                    .containsExactlyInAnyOrder(retainedIds.get(0), retainedIds.get(1));
            assertThat(mappings.getFieldMappingsByIntegration(fixture.id())).singleElement()
                    .satisfies(mapping -> assertThat(mapping.getId()).isEqualTo(retainedIds.get(2)));
        } finally {
            cleanup(fixture);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void invalidSamplePreservesOldSchemasAndMappings(boolean middleware) {
        Fixture fixture = fixture(middleware);
        try {
            MappingSchemaRequestDTO request = request(firstType(middleware));
            request.setSchemaDefinition(null);

            assertThatThrownBy(() -> mappings.saveSchema(fixture.id(), request))
                    .isInstanceOfSatisfying(PayloadValidationException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo("INVALID_JSON"));

            assertOldState(fixture);
        } finally {
            cleanup(fixture);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void failureAfterCleanupRollsBackNewSampleAndRestoresOldRecords(boolean middleware) {
        Fixture fixture = fixture(middleware);
        try {
            doAnswer(invocation -> {
                schemas.flush();
                assertThat(schemas.findAllById(fixture.oldSchemaIds())).isEmpty();
                assertThat(fields.findByIntegrationId(fixture.id())).isEmpty();
                assertThat(schemas.findByIntegrationId(fixture.id())).hasSize(1);
                throw new IllegalStateException("Failure after flow cleanup");
            }).doCallRealMethod().when(mapper).toSchemaResponse(any(MappingSchema.class));

            assertThatThrownBy(() -> mappings.saveSchema(fixture.id(), request(firstType(middleware))))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Failure after flow cleanup");

            assertOldState(fixture);
        } finally {
            cleanup(fixture);
        }
    }

    private Fixture fixture(boolean newMiddleware) {
        return new TransactionTemplate(transactions).execute(status -> {
            CatalogSystem source = systems.save(CatalogSystem.builder().name("Flow test source").description("").build());
            CatalogSystem destination = systems.save(CatalogSystem.builder().name("Flow test destination").description("").build());
            CatalogSystem middleware = systems.save(CatalogSystem.builder().name("Flow test middleware").description("").build());
            Integration integration = integration(source, destination, middleware, !newMiddleware);
            List<MappingSchema> oldSchemas = Arrays.stream(SchemaType.values())
                    .filter(type -> type.isCompatibleWithFlow(!newMiddleware))
                    .map(type -> sample(integration, type)).toList();
            List<Long> oldMappingIds = oldSchemas.stream().map(schema -> link(integration, schema).getId()).toList();
            Integration neighbor = integration(source, destination, middleware, !newMiddleware);
            link(neighbor, sample(neighbor, firstType(!newMiddleware)));

            integration.setHasMiddleware(newMiddleware);
            integration.setMiddlewareSystem(newMiddleware ? middleware : null);
            integration.setReviewRequired(true);
            return new Fixture(integration.getId(), neighbor.getId(), source.getId(), destination.getId(), middleware.getId(),
                    oldSchemas.stream().map(MappingSchema::getId).toList(), oldMappingIds);
        });
    }

    private Integration integration(CatalogSystem source, CatalogSystem destination, CatalogSystem middleware, boolean hasMiddleware) {
        return integrations.save(Integration.builder().name("Flow test").description("")
                .sourceSystem(source).destinationSystem(destination).middlewareSystem(hasMiddleware ? middleware : null)
                .hasMiddleware(hasMiddleware).isSync(true).sourcePayloadFormat(PayloadFormat.JSON)
                .destinationPayloadFormat(PayloadFormat.JSON).status(IntegrationStatus.DRAFT).build());
    }

    private MappingSchema sample(Integration integration, SchemaType type) {
        MappingSchema schema = schemas.save(MappingSchema.builder().integration(integration).schemaType(type)
                .format(PayloadFormat.JSON).schemaDefinition(DEFINITION).sampleFile(SAMPLE)
                .fileName("old.json").contentType("application/json").build());
        integration.getMappingSchemas().add(schema);
        return schema;
    }

    private FieldMapping link(Integration integration, MappingSchema schema) {
        FieldMapping mapping = fields.save(FieldMapping.builder().integration(integration)
                .sourceSchema(schema).targetSchema(schema).sourceFieldPath("id").targetFieldPath("id")
                .isAiGenerated(false).build());
        integration.getFieldMappings().add(mapping);
        return mapping;
    }

    private void assertOldState(Fixture fixture) {
        assertThat(mappings.getSchemasByIntegration(fixture.id())).extracting(MappingSchemaResponseDTO::getId)
                .containsExactlyInAnyOrderElementsOf(fixture.oldSchemaIds());
        assertThat(mappings.getFieldMappingsByIntegration(fixture.id())).extracting(mapping -> mapping.getId())
                .containsExactlyInAnyOrderElementsOf(fixture.oldMappingIds());
        assertThat(mappings.getSampleFile(fixture.id(), fixture.oldSchemaIds().getFirst()).bytes()).isEqualTo(SAMPLE);
    }

    private void cleanup(Fixture fixture) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (Long id : List.of(fixture.id(), fixture.neighborId())) {
                fields.deleteByIntegrationId(id);
                fields.flush();
                schemas.deleteAll(schemas.findByIntegrationId(id));
                schemas.flush();
                integrations.deleteById(id);
            }
            integrations.flush();
            systems.deleteById(fixture.sourceSystemId());
            systems.deleteById(fixture.destinationSystemId());
            systems.deleteById(fixture.middlewareSystemId());
        });
    }

    private SchemaType firstType(boolean middleware) {
        return middleware ? SchemaType.SOURCE_TO_MW_REQUEST : SchemaType.SOURCE_TO_DEST_REQUEST;
    }

    private MappingSchemaRequestDTO request(SchemaType type) {
        return MappingSchemaRequestDTO.builder().schemaType(type).format(PayloadFormat.JSON)
                .schemaDefinition(DEFINITION).build();
    }

    private record Fixture(Long id, Long neighborId, Long sourceSystemId, Long destinationSystemId, Long middlewareSystemId,
                           List<Long> oldSchemaIds, List<Long> oldMappingIds) {}
}
