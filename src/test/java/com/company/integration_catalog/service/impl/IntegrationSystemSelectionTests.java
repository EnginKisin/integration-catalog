package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.security.*;
import com.company.integration_catalog.validation.IntegrationCompletionValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationSystemSelectionTests {
    @Mock IntegrationRepository integrations;
    @Mock SystemRepository systems;
    @Mock EnvironmentConfigRepository configs;
    @Mock CredentialCrypto crypto;
    @Mock CredentialAccess access;
    private IntegrationServiceImpl service;

    @BeforeEach void setUp() {
        service = new IntegrationServiceImpl(integrations, configs, new IntegrationMapper(), systems, new IntegrationCompletionValidator(), crypto, access);
    }

    @ParameterizedTest @ValueSource(strings = {"source", "destination", "middleware"})
    void createRejectsInactiveSelections(String inactive) {
        var integration = existing(); deactivate(integration, inactive);
        var request = request(integration); stubSystems(integration);
        assertThatThrownBy(() -> service.createIntegration(request)).isInstanceOf(ResponseStatusException.class);
        verify(integrations, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"source", "destination", "middleware"})
    void existingInactiveSelectionCanBeRetainedDuringMetadataUpdate(String inactive) {
        var integration = existing(); deactivate(integration, inactive);
        var request = request(integration); request.setName("Renamed"); stubUpdate(integration); stubSystems(integration);
        var response = service.updateIntegration(1L, request);
        assertThat(response.getName()).isEqualTo("Renamed");
        assertThat(response.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
        assertThat(response.isReviewRequired()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"source", "destination", "middleware"})
    void replacingSelectionWithInactiveSystemDoesNotModifyIntegration(String changed) {
        var integration = existing(); var replacement = existing();
        changeId(replacement, changed); deactivate(replacement, changed);
        var request = request(replacement); stubSystems(replacement);
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        assertThatThrownBy(() -> service.updateIntegration(1L, request)).isInstanceOf(ResponseStatusException.class);
        assertThat(integration.getSourceSystem().getId()).isEqualTo(10L);
        assertThat(integration.getDestinationSystem().getId()).isEqualTo(20L);
        assertThat(integration.getMiddlewareSystem().getId()).isEqualTo(30L);
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
        verify(integrations, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"source", "destination", "middleware", "format", "sync"})
    void technicalChangesPreserveChildrenButRequireReviewAndRecompletion(String changed) {
        var integration = existing(); var replacement = existing();
        if (Set.of("source", "destination", "middleware").contains(changed)) changeId(replacement, changed);
        var request = request(replacement);
        if (changed.equals("format")) request.setSourcePayloadFormat(PayloadFormat.XML);
        if (changed.equals("sync")) request.setIsSync(true);
        var schema = MappingSchema.builder().id(100L).build();
        var mapping = FieldMapping.builder().id(200L).build();
        var config = EnvironmentConfig.builder().id(300L).build();
        integration.getMappingSchemas().add(schema); integration.getFieldMappings().add(mapping);
        integration.getEnvironmentConfigs().add(config);
        stubUpdate(integration); stubSystems(replacement);

        var response = service.updateIntegration(1L, request);

        assertThat(response.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
        assertThat(response.isReviewRequired()).isTrue();
        assertThat(integration.getMappingSchemas()).containsExactly(schema);
        assertThat(integration.getFieldMappings()).containsExactly(mapping);
        assertThat(integration.getEnvironmentConfigs()).containsExactly(config);
        verifyNoInteractions(configs);
    }

    @Test void configSaveReturnsCompletedIntegrationToDraftAndDoesNotClearPendingReview() {
        var integration = existing(); integration.setReviewRequired(true);
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        var request = EnvironmentConfigRequestDTO.builder().environmentType(EnvironmentType.DEV)
                .configTarget(ConfigTarget.DESTINATION).url("https://example.com").authType(AuthType.NONE).build();
        service.saveEnvironmentConfigs(1L, List.of(request));
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
        assertThat(integration.isReviewRequired()).isTrue();
    }

    private void stubUpdate(Integration integration) {
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        when(integrations.save(integration)).thenReturn(integration);
    }

    @Test void aSharedSystemIsLockedOnceEvenWhenItFillsEveryRole() {
        var shared = CatalogSystem.builder().id(10L).name("SAP").build();
        when(systems.findByIdForUpdate(10L)).thenReturn(Optional.of(shared));
        when(integrations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = request(existing());
        request.setDestinationSystemId(10L);
        request.setMiddlewareSystemId(10L);

        var response = service.createIntegration(request);

        assertThat(response.getSourceSystemId()).isEqualTo(10L);
        assertThat(response.getDestinationSystemId()).isEqualTo(10L);
        assertThat(response.getMiddlewareSystemId()).isEqualTo(10L);
        verify(systems, times(1)).findByIdForUpdate(10L);
    }

    @Test void inactiveSelectionCannotBeMovedIntoAnotherRole() {
        var integration = existing();
        integration.getSourceSystem().setActive(false);
        stubSystems(integration);
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        var request = request(integration);
        request.setSourceSystemId(20L);
        request.setDestinationSystemId(10L);

        assertThatThrownBy(() -> service.updateIntegration(1L, request)).isInstanceOf(ResponseStatusException.class);
        assertThat(integration.getSourceSystem().getId()).isEqualTo(10L);
        assertThat(integration.getDestinationSystem().getId()).isEqualTo(20L);
        verify(integrations, never()).save(any());
    }
    private void stubSystems(Integration integration) {
        when(systems.findByIdForUpdate(integration.getSourceSystem().getId())).thenReturn(Optional.of(integration.getSourceSystem()));
        when(systems.findByIdForUpdate(integration.getDestinationSystem().getId())).thenReturn(Optional.of(integration.getDestinationSystem()));
        when(systems.findByIdForUpdate(integration.getMiddlewareSystem().getId())).thenReturn(Optional.of(integration.getMiddlewareSystem()));
    }
    private void deactivate(Integration integration, String type) {
        switch (type) {
            case "source" -> integration.getSourceSystem().setActive(false);
            case "destination" -> integration.getDestinationSystem().setActive(false);
            case "middleware" -> integration.getMiddlewareSystem().setActive(false);
        }
    }
    private void changeId(Integration integration, String type) {
        switch (type) {
            case "source" -> integration.getSourceSystem().setId(11L);
            case "destination" -> integration.getDestinationSystem().setId(21L);
            case "middleware" -> integration.getMiddlewareSystem().setId(31L);
        }
    }
    private Integration existing() {
        return Integration.builder().id(1L).name("Orders").description("Order transfer")
                .sourceSystem(CatalogSystem.builder().id(10L).build()).destinationSystem(CatalogSystem.builder().id(20L).build())
                .middlewareSystem(CatalogSystem.builder().id(30L).build()).hasMiddleware(true).isSync(false)
                .sourcePayloadFormat(PayloadFormat.JSON).destinationPayloadFormat(PayloadFormat.JSON)
                .status(IntegrationStatus.COMPLETED).build();
    }
    private IntegrationRequestDTO request(Integration integration) {
        return IntegrationRequestDTO.builder().name(integration.getName()).description(integration.getDescription())
                .sourceSystemId(integration.getSourceSystem().getId()).destinationSystemId(integration.getDestinationSystem().getId())
                .middlewareSystemId(integration.getMiddlewareSystem().getId()).hasMiddleware(true).isSync(false)
                .sourcePayloadFormat(PayloadFormat.JSON).destinationPayloadFormat(PayloadFormat.JSON).build();
    }
}
