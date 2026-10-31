package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.exception.IntegrationCompletionException;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.validation.IntegrationCompletionValidator;
import com.company.integration_catalog.security.CredentialCrypto;
import com.company.integration_catalog.security.CredentialAccess;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.company.integration_catalog.payload.PayloadSchemas;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;

@ExtendWith(MockitoExtension.class)
class IntegrationCompletionTests {
    @Mock private IntegrationRepository repository;
    @Mock private EnvironmentConfigRepository configRepository;
    @Mock private SystemRepository systemRepository;
    @Mock private CredentialCrypto crypto;
    @Mock private CredentialAccess credentialAccess;
    private IntegrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IntegrationServiceImpl(repository, configRepository, new IntegrationMapper(),
                systemRepository, new IntegrationCompletionValidator(),
                crypto, credentialAccess);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void completesAllFlowTypesWithoutRequiringManualMappings(boolean middleware, boolean sync) {
        Integration integration = ready(middleware, sync);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));

        service.completeIntegration(1L);

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
        verify(repository).save(integration);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void completesWithValidMappings(boolean middleware, boolean sync) {
        Integration integration = ready(middleware, sync);
        addMapping(integration);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));

        service.completeIntegration(1L);

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
    }

    @Test
    void completesCsvFlowWithoutAnyFieldMappingsAndRejectsStaleMappings() {
        Integration integration = ready(false, false);
        integration.setSourcePayloadFormat(PayloadFormat.CSV);
        integration.setDestinationPayloadFormat(PayloadFormat.CSV);
        MappingSchema csv = PayloadSchemas.prepare(integration, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_DEST_REQUEST).format(PayloadFormat.CSV).rawContent("id,name\n1,Ali").build(), null);
        csv.setId(1L);
        integration.getMappingSchemas().set(0, csv);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        service.completeIntegration(1L);
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
        addMapping(integration);
        assertThatThrownBy(() -> service.completeIntegration(1L))
                .isInstanceOfSatisfying(IntegrationCompletionException.class,
                        ex -> assertThat(ex.getDetails()).anyMatch(detail -> detail.contains("do not support field mapping")));
    }

    @Test
    void rejectsMissingSamplesAndFormatMismatchAtCompletion() {
        Integration integration = ready(false, false);
        integration.setSourcePayloadFormat(PayloadFormat.EXCEL);
        MappingSchema schema = integration.getMappingSchemas().get(0);
        schema.setFormat(PayloadFormat.EXCEL);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        assertThatThrownBy(() -> service.completeIntegration(1L))
                .isInstanceOfSatisfying(IntegrationCompletionException.class,
                        ex -> assertThat(ex.getDetails()).anyMatch(detail -> detail.contains("sample file is required")));
        schema.setFormat(PayloadFormat.JSON);
        assertThatThrownBy(() -> service.completeIntegration(1L))
                .isInstanceOfSatisfying(IntegrationCompletionException.class,
                        ex -> assertThat(ex.getDetails()).anyMatch(detail -> detail.contains("Expected EXCEL")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-config", "missing-target", "split-environments", "duplicate-config",
            "empty-url", "missing-auth", "missing-schema", "empty-schema", "duplicate-schema",
            "foreign-schema", "empty-path", "reversed-mapping", "basic-information", "middleware-selection",
            "incompatible-schema"})
    void rejectsIncompleteIntegrationWithoutChangingStatus(String problem) {
        Integration integration = ready(true, true);
        switch (problem) {
            case "no-config" -> integration.getEnvironmentConfigs().clear();
            case "missing-target" -> integration.getEnvironmentConfigs().remove(0);
            case "split-environments" -> integration.getEnvironmentConfigs().get(0).setEnvironmentType(EnvironmentType.PROD);
            case "duplicate-config" -> integration.getEnvironmentConfigs().add(config(ConfigTarget.DESTINATION));
            case "empty-url" -> integration.getEnvironmentConfigs().get(0).setUrl("  ");
            case "missing-auth" -> integration.getEnvironmentConfigs().get(0).setAuthType(null);
            case "missing-schema" -> integration.getMappingSchemas().remove(0);
            case "empty-schema" -> integration.getMappingSchemas().get(0).setSchemaDefinition(Map.of());
            case "duplicate-schema" -> integration.getMappingSchemas().add(integration.getMappingSchemas().get(0));
            case "foreign-schema" -> {
                addMapping(integration);
                integration.getFieldMappings().get(0).setTargetSchema(MappingSchema.builder().id(99L).build());
            }
            case "empty-path" -> {
                addMapping(integration);
                integration.getFieldMappings().get(0).setSourceFieldPath(" ");
            }
            case "reversed-mapping" -> {
                addMapping(integration);
                FieldMapping mapping = integration.getFieldMappings().get(0);
                MappingSchema source = mapping.getSourceSchema();
                mapping.setSourceSchema(mapping.getTargetSchema());
                mapping.setTargetSchema(source);
            }
            case "basic-information" -> integration.setSourceSystem(null);
            case "middleware-selection" -> integration.setMiddlewareSystem(null);
            case "incompatible-schema" -> integration.getMappingSchemas().get(0).setSchemaType(SchemaType.SOURCE_TO_DEST_REQUEST);
            default -> throw new AssertionError(problem);
        }
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));

        assertThatThrownBy(() -> service.completeIntegration(1L))
                .isInstanceOfSatisfying(IntegrationCompletionException.class,
                        ex -> assertThat(ex.getDetails()).isNotEmpty());

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void reportsMultipleProblemsTogether() {
        Integration integration = ready(false, false);
        integration.getEnvironmentConfigs().clear();
        integration.getMappingSchemas().clear();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));

        assertThatThrownBy(() -> service.completeIntegration(1L))
                .isInstanceOfSatisfying(IntegrationCompletionException.class, ex -> {
                    assertThat(ex.getDetails()).containsExactly(
                            "At least one environment configuration is required",
                            "Required schema is missing: SOURCE_TO_DEST_REQUEST");
                    assertThat(ex.getViolations()).extracting(com.company.integration_catalog.exception.Violation::code)
                            .containsExactly("ENVIRONMENT_CONFIG_REQUIRED", "REQUIRED_SCHEMA_MISSING");
                    assertThat(ex.getViolations().get(1).params()).containsEntry("schemaType", SchemaType.SOURCE_TO_DEST_REQUEST);
                });
    }

    @Test
    void repeatedCompletionStillValidatesTheCurrentData() {
        Integration integration = ready(false, false);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        service.completeIntegration(1L);
        service.completeIntegration(1L);
        verify(repository, times(2)).save(integration);

        integration.getMappingSchemas().clear();
        assertThatThrownBy(() -> service.completeIntegration(1L)).isInstanceOf(IntegrationCompletionException.class);
        verify(repository, times(2)).save(integration);
    }

    @Test
    void missingIntegrationReturnsNotFound() {
        assertThatThrownBy(() -> service.completeIntegration(1L)).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void preservedDataMustBeReviewedBeforeRecompletion() {
        Integration integration = ready(false, false);
        integration.setReviewRequired(true);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        assertThatThrownBy(() -> service.completeIntegration(1L))
                .isInstanceOfSatisfying(IntegrationCompletionException.class,
                        ex -> assertThat(ex.getDetails()).anyMatch(message -> message.contains("confirm the review")));
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void reviewConfirmationPreservesDataAndDoesNotAutomaticallyCompleteIntegration() {
        Integration integration = ready(false, false);
        integration.setReviewRequired(true);
        MappingSchema schema = integration.getMappingSchemas().get(0);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        when(repository.save(integration)).thenReturn(integration);
        var response = service.confirmIntegrationReview(1L);
        assertThat(response.isReviewRequired()).isFalse();
        assertThat(response.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
        assertThat(integration.getMappingSchemas()).containsExactly(schema);
        service.completeIntegration(1L);
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
    }

    private Integration ready(boolean middleware, boolean sync) {
        Integration integration = Integration.builder().id(1L).name("Orders").description("Order transfer")
                .sourceSystem(CatalogSystem.builder().id(10L).build()).destinationSystem(CatalogSystem.builder().id(20L).build())
                .hasMiddleware(middleware).middlewareSystem(middleware ? CatalogSystem.builder().id(30L).build() : null)
                .isSync(sync).sourcePayloadFormat(PayloadFormat.JSON).destinationPayloadFormat(PayloadFormat.JSON)
                .status(IntegrationStatus.DRAFT).build();
        integration.getEnvironmentConfigs().add(config(ConfigTarget.DESTINATION));
        if (middleware) {
            integration.getEnvironmentConfigs().add(config(ConfigTarget.MIDDLEWARE));
        }
        List<SchemaType> types = new ArrayList<>(middleware
                ? List.of(SchemaType.SOURCE_TO_MW_REQUEST, SchemaType.MW_TO_DEST_REQUEST)
                : List.of(SchemaType.SOURCE_TO_DEST_REQUEST));
        if (sync) {
            types.addAll(middleware ? List.of(SchemaType.DEST_TO_MW_RESPONSE, SchemaType.MW_TO_SOURCE_RESPONSE)
                    : List.of(SchemaType.DEST_TO_SOURCE_RESPONSE));
        }
        for (SchemaType type : types) {
            integration.getMappingSchemas().add(MappingSchema.builder()
                    .id((long) integration.getMappingSchemas().size() + 1).integration(integration)
                    .schemaType(type).schemaDefinition(Map.of("type", "object")).build());
        }
        return integration;
    }

    private EnvironmentConfig config(ConfigTarget target) {
        return EnvironmentConfig.builder().configTarget(target).environmentType(EnvironmentType.DEV)
                .url("https://example.com").authType(AuthType.NONE).build();
    }

    private void addMapping(Integration integration) {
        MappingSchema source = integration.getMappingSchemas().get(0);
        MappingSchema target = integration.getMappingSchemas().get(Boolean.TRUE.equals(integration.getHasMiddleware()) ? 1 : 0);
        integration.getFieldMappings().add(FieldMapping.builder().integration(integration)
                .sourceSchema(source).targetSchema(target).sourceFieldPath("id").targetFieldPath("id").build());
    }
}
