package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.system.SystemRequestDTO;
import com.company.integration_catalog.model.entity.CatalogSystem;
import com.company.integration_catalog.repository.SystemRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemManagementTests {
    @Mock SystemRepository repository;
    @Mock IntegrationRepository integrations;
    private SystemServiceImpl service;

    @BeforeEach void setUp() { service = new SystemServiceImpl(repository, integrations); }

    @Test void listsInactiveSystemsAndUsageCountsWithoutLosingUnusedItems() {
        var inactive = CatalogSystem.builder().id(1L).name("Old").active(false).build();
        var active = CatalogSystem.builder().id(2L).name("New").build();
        when(repository.findAll()).thenReturn(List.of(inactive, active));
        var usage = mock(IntegrationRepository.SystemUsage.class);
        when(usage.getSystemId()).thenReturn(1L); when(usage.getUsageCount()).thenReturn(4L);
        when(integrations.systemUsageCounts()).thenReturn(List.of(usage));
        var result = service.getAllSystems();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).isActive()).isFalse();
        assertThat(result.get(0).getUsageCount()).isEqualTo(4);
        assertThat(result.get(1).isActive()).isTrue();
        assertThat(result.get(1).getUsageCount()).isZero();
    }

    @Test void createsActiveUnusedSystem() {
        when(repository.save(any())).thenAnswer(invocation -> {
            CatalogSystem item = invocation.getArgument(0); item.setId(1L); return item;
        });
        var result = service.createSystem(new SystemRequestDTO("New", "Description"));
        assertThat(result.isActive()).isTrue(); assertThat(result.getUsageCount()).isZero();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test void updatesSharedMetadataWithoutChangingIdentityOrActiveState() {
        var item = CatalogSystem.builder().id(1L).name("Old").active(false).build();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(repository.save(item)).thenReturn(item);
        when(integrations.countBySystemId(1L)).thenReturn(4L);
        var result = service.updateSystem(1L, new SystemRequestDTO("Renamed", "Updated"));
        assertThat(result.getId()).isEqualTo(1L); assertThat(result.getName()).isEqualTo("Renamed");
        assertThat(result.getDescription()).isEqualTo("Updated"); assertThat(result.isActive()).isFalse();
        assertThat(result.getUsageCount()).isEqualTo(4);
        verify(repository, never()).delete(any());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void canDeactivateAndReactivateUsedSystems(boolean active) {
        var item = CatalogSystem.builder().id(1L).active(!active).build();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(repository.save(item)).thenReturn(item);
        when(integrations.countBySystemId(1L)).thenReturn(4L);
        var result = service.setSystemActive(1L, active);
        assertThat(result.isActive()).isEqualTo(active); assertThat(result.getUsageCount()).isEqualTo(4);
        verify(repository, never()).delete(any());
    }

    @Test void usedSystemCannotBeDeletedEvenWhenInactive() {
        var item = CatalogSystem.builder().id(1L).active(false).build();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(integrations.countBySystemId(1L)).thenReturn(4L);
        assertThatThrownBy(() -> service.deleteSystem(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).contains("4", "deactivate");
                });
        verify(repository, never()).delete(any());
    }

    @Test void unusedSystemCanBeDeleted() {
        var item = CatalogSystem.builder().id(1L).build();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        service.deleteSystem(1L);
        verify(repository).delete(item);
    }

    @Test void missingSystemReturnsNotFound() {
        assertThatThrownBy(() -> service.deleteSystem(1L)).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).delete(any()); verifyNoInteractions(integrations);
    }
}
