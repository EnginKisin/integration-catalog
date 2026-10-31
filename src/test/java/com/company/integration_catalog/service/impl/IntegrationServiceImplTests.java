package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.CatalogSystem;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.repository.EnvironmentConfigRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.repository.SystemRepository;
import jakarta.persistence.EntityNotFoundException;
import com.company.integration_catalog.validation.IntegrationCompletionValidator;
import com.company.integration_catalog.security.CredentialCrypto;
import com.company.integration_catalog.security.CredentialAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceImplTests {

    @Mock private IntegrationRepository integrationRepository;
    @Mock private EnvironmentConfigRepository configRepository;
    @Mock private SystemRepository systemRepository;
    @Mock private CredentialAccess credentialAccess;
    private final CredentialCrypto crypto = new CredentialCrypto(
            java.util.Base64.getEncoder().encodeToString(new byte[32]), new ObjectMapper());

    private IntegrationServiceImpl service;
    private final CatalogSystem source = CatalogSystem.builder().id(10L).build();
    private final CatalogSystem destination = CatalogSystem.builder().id(20L).build();
    private final CatalogSystem middleware = CatalogSystem.builder().id(30L).build();

    @BeforeEach
    void setUp() {
        service = new IntegrationServiceImpl(integrationRepository, configRepository,
                new IntegrationMapper(), systemRepository,
                new IntegrationCompletionValidator(), crypto, credentialAccess);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updatesSyncModeInBothSavedEntityAndResponse(boolean requestedSync) {
        Integration existing = Integration.builder().id(1L).isSync(!requestedSync).build();
        when(integrationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(integrationRepository.save(any(Integration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegrationRequestDTO request = IntegrationRequestDTO.builder()
                .name("Order integration").description("Transfers orders")
                .sourceSystemId(10L).destinationSystemId(20L)
                .sourcePayloadFormat(PayloadFormat.JSON)
                .destinationPayloadFormat(PayloadFormat.JSON)
                .hasMiddleware(false).isSync(requestedSync)
                .build();
        when(systemRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(systemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destination));

        IntegrationResponseDTO response = service.updateIntegration(1L, request);

        ArgumentCaptor<Integration> captor = ArgumentCaptor.forClass(Integration.class);
        verify(integrationRepository).save(captor.capture());
        assertThat(captor.getValue().getIsSync()).isEqualTo(requestedSync);
        assertThat(response.getIsSync()).isEqualTo(requestedSync);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void savesResolvedSystemsOnCreateAndUpdate(boolean creating, boolean withMiddleware) {
        Integration existing = Integration.builder().id(1L).middlewareSystem(middleware).build();
        if (!creating) {
            when(integrationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        }
        when(systemRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(systemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destination));
        if (withMiddleware) {
            when(systemRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(middleware));
        }
        when(integrationRepository.save(any(Integration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        IntegrationRequestDTO request = request(withMiddleware);

        IntegrationResponseDTO response = creating ? service.createIntegration(request)
                : service.updateIntegration(1L, request);

        ArgumentCaptor<Integration> captor = ArgumentCaptor.forClass(Integration.class);
        verify(integrationRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceSystem()).isSameAs(source);
        assertThat(captor.getValue().getDestinationSystem()).isSameAs(destination);
        assertThat(captor.getValue().getMiddlewareSystem()).isSameAs(withMiddleware ? middleware : null);
        assertThat(response.getSourceSystemId()).isEqualTo(10L);
        assertThat(response.getDestinationSystemId()).isEqualTo(20L);
        assertThat(response.getMiddlewareSystemId()).isEqualTo(withMiddleware ? 30L : null);
        if (!withMiddleware) {
            verify(systemRepository, never()).findByIdForUpdate(30L);
        }
    }

    @ParameterizedTest
    @CsvSource({"true,Source", "true,Destination", "true,Middleware",
            "false,Source", "false,Destination", "false,Middleware"})
    void rejectsMissingSystemsBeforeSavingOrModifyingExistingIntegration(boolean creating, String missing) {
        CatalogSystem originalSource = CatalogSystem.builder().id(100L).build();
        CatalogSystem originalDestination = CatalogSystem.builder().id(200L).build();
        CatalogSystem originalMiddleware = CatalogSystem.builder().id(300L).build();
        Integration existing = Integration.builder().id(1L).name("Original")
                .sourceSystem(originalSource).destinationSystem(originalDestination).middlewareSystem(originalMiddleware).build();
        if (!creating) {
            when(integrationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        }
        if (!missing.equals("Source")) {
            when(systemRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        }
        if (missing.equals("Middleware")) {
            when(systemRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destination));
        }
        IntegrationRequestDTO request = request(true);

        assertThatThrownBy(() -> {
            if (creating) {
                service.createIntegration(request);
            } else {
                service.updateIntegration(1L, request);
            }
        }).isInstanceOf(EntityNotFoundException.class).hasMessageStartingWith("System not found with id:");

        verify(integrationRepository, never()).save(any());
        assertThat(existing.getName()).isEqualTo("Original");
        assertThat(existing.getSourceSystem()).isSameAs(originalSource);
        assertThat(existing.getDestinationSystem()).isSameAs(originalDestination);
        assertThat(existing.getMiddlewareSystem()).isSameAs(originalMiddleware);
    }

    @Test
    void missingIntegrationIsRejectedBeforeResolvingSystems() {
        assertThatThrownBy(() -> service.updateIntegration(1L, request(true)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Integration not found with id: 1");
        verifyNoInteractions(systemRepository);
        verify(integrationRepository, never()).save(any());
    }

    private IntegrationRequestDTO request(boolean withMiddleware) {
        return IntegrationRequestDTO.builder().name("Orders").description("Order transfer")
                .sourceSystemId(10L).destinationSystemId(20L).middlewareSystemId(withMiddleware ? 30L : null)
                .sourcePayloadFormat(PayloadFormat.JSON).destinationPayloadFormat(PayloadFormat.JSON)
                .isSync(false).hasMiddleware(withMiddleware).build();
    }

    @ParameterizedTest
    @CsvSource({"API_KEY,false,old", "API_KEY,true,new", "NONE,false,cleared", "BASIC_AUTH,false,cleared"})
    void preservesOmittedCredentialsOnlyForSameEnvironmentTargetAndAuthType(
            AuthType requestedType, boolean replace, String expected) {
        Integration integration = Integration.builder().id(1L).hasMiddleware(false).build();
        when(integrationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        EnvironmentConfig old = EnvironmentConfig.builder().environmentType(EnvironmentType.DEV)
                .configTarget(ConfigTarget.DESTINATION).authType(AuthType.API_KEY)
                .authCredentials(Map.of("apiKey", "old")).build();
        EnvironmentConfig unrelated = EnvironmentConfig.builder().environmentType(EnvironmentType.PROD)
                .configTarget(ConfigTarget.DESTINATION).authType(requestedType)
                .authCredentials(Map.of("apiKey", "unrelated")).build();
        when(configRepository.findByIntegrationId(1L)).thenReturn(List.of(old, unrelated));
        EnvironmentConfigRequestDTO request = EnvironmentConfigRequestDTO.builder()
                .environmentType(EnvironmentType.DEV).configTarget(ConfigTarget.DESTINATION)
                .authType(requestedType).url("https://example.com")
                .authCredentials(replace ? Map.of("apiKey", "new") : null).build();

        if (requestedType == AuthType.BASIC_AUTH) {
            assertThatThrownBy(() -> service.saveEnvironmentConfigs(1L, List.of(request)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verify(configRepository, never()).saveAll(any());
            verify(configRepository, never()).deleteAll(any());
            return;
        }
        service.saveEnvironmentConfigs(1L, List.of(request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EnvironmentConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(configRepository).saveAll(captor.capture());
        if (expected.equals("cleared")) {
            assertThat(captor.getValue().get(0).getAuthCredentials()).isNull();
        } else {
            EnvironmentConfig saved = captor.getValue().get(0);
            if (replace) {
                assertThat(crypto.decrypt(saved.getAuthCredentials(), saved)).containsEntry("apiKey", expected);
            } else {
                assertThat(saved.getAuthCredentials()).containsEntry("apiKey", expected);
            }
        }
    }
}
