package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.payload.PayloadSchemas;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.FieldMapping;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.repository.FieldMappingRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.repository.MappingSchemaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MappingServiceImplTests {

    @Mock private IntegrationRepository integrationRepository;
    @Mock private MappingSchemaRepository schemaRepository;
    @Mock private FieldMappingRepository fieldMappingRepository;

    private MappingServiceImpl service;
    private final Integration integration = Integration.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        service = new MappingServiceImpl(integrationRepository, schemaRepository,
                fieldMappingRepository, new IntegrationMapper());
        when(integrationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
    }

    @Test
    void savesMappingsUsingSchemasFromTheSameIntegration() {
        integration.setStatus(com.company.integration_catalog.model.enums.IntegrationStatus.COMPLETED);
        MappingSchema source = schema(10L);
        MappingSchema target = schema(20L);
        when(schemaRepository.findByIdAndIntegrationId(10L, 1L)).thenReturn(Optional.of(source));
        when(schemaRepository.findByIdAndIntegrationId(20L, 1L)).thenReturn(Optional.of(target));

        service.saveFieldMappings(1L, List.of(request(10L, 20L)));

        assertThat(integration.getStatus()).isEqualTo(com.company.integration_catalog.model.enums.IntegrationStatus.DRAFT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(mapping -> {
            assertThat(mapping.getIntegration()).isSameAs(integration);
            assertThat(mapping.getSourceSchema()).isSameAs(source);
            assertThat(mapping.getTargetSchema()).isSameAs(target);
            assertThat(mapping.getSourceFieldPath()).isEqualTo("customer.id");
            assertThat(mapping.getTargetFieldPath()).isEqualTo("client.id");
        });
        verify(fieldMappingRepository).deleteByIntegrationId(1L);
    }

    @ParameterizedTest
    @ValueSource(longs = {10L, 20L})
    void rejectsUnavailableSourceOrTargetWithoutChangingExistingMappings(long unavailableId) {
        if (unavailableId == 20L) {
            when(schemaRepository.findByIdAndIntegrationId(10L, 1L))
                    .thenReturn(Optional.of(schema(10L)));
        }

        when(schemaRepository.findByIdAndIntegrationId(unavailableId, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveFieldMappings(1L, List.of(request(10L, 20L))))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(fieldMappingRepository);
    }

    @Test
    void validatesTheWholeBatchBeforeReplacingExistingMappings() {
        when(schemaRepository.findByIdAndIntegrationId(10L, 1L)).thenReturn(Optional.of(schema(10L)));
        when(schemaRepository.findByIdAndIntegrationId(20L, 1L)).thenReturn(Optional.of(schema(20L)));
        when(schemaRepository.findByIdAndIntegrationId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveFieldMappings(1L,
                List.of(request(10L, 20L), request(10L, 99L))))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(fieldMappingRepository);
    }

    @Test
    void emptyBatchStillClearsExistingMappings() {
        service.saveFieldMappings(1L, List.of());

        verify(fieldMappingRepository).deleteByIntegrationId(1L);
        verify(fieldMappingRepository).saveAll(List.of());
        verifyNoInteractions(schemaRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CSV", "EXCEL"})
    void rejectsSampleOnlyMappingEndpointsBeforeDeletingAnyExistingMappings(String format) {
        MappingSchema source = schema(10L);
        source.setFormat(PayloadFormat.valueOf(format));
        when(schemaRepository.findByIdAndIntegrationId(10L, 1L)).thenReturn(Optional.of(source));
        when(schemaRepository.findByIdAndIntegrationId(20L, 1L)).thenReturn(Optional.of(schema(20L)));
        assertThatThrownBy(() -> service.saveFieldMappings(1L, List.of(request(10L, 20L))))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("MAPPING_NOT_SUPPORTED"));
        verifyNoInteractions(fieldMappingRepository);
    }

    @Test
    void upsertsXmlWithStableIdAndPreservesRawContentInGet() {
        integration.setSourcePayloadFormat(PayloadFormat.XML);
        String xml = "<order>\r\n <id>42</id>\r\n</order>";
        MappingSchema existing = schema(10L);
        existing.setSchemaType(SchemaType.SOURCE_TO_DEST_REQUEST);
        when(schemaRepository.findByIntegrationIdAndSchemaType(1L, SchemaType.SOURCE_TO_DEST_REQUEST)).thenReturn(Optional.of(existing));
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.saveSchema(1L, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_DEST_REQUEST).format(PayloadFormat.XML).rawContent(xml).build());
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRawContent()).isEqualTo(xml);
        assertThat(response.isMappingSupported()).isTrue();
        assertThat(response.getSchemaDefinition()).containsKey("fields");
        when(integrationRepository.existsById(1L)).thenReturn(true);
        when(schemaRepository.findByIntegrationId(1L)).thenReturn(List.of(existing));
        assertThat(service.getSchemasByIntegration(1L)).singleElement().satisfies(saved -> assertThat(saved.getRawContent()).isEqualTo(xml));
    }

    @Test
    void rejectsRemovedXmlFieldsWithoutMutatingStoredSchema() {
        integration.setSourcePayloadFormat(PayloadFormat.XML);
        MappingSchema existing = schema(10L);
        existing.setFormat(PayloadFormat.XML);
        existing.setRawContent("<order><id>42</id></order>");
        existing.setSchemaDefinition(PayloadSchemas.xmlDefinition(existing.getRawContent(), false));
        when(schemaRepository.findByIntegrationIdAndSchemaType(1L, SchemaType.SOURCE_TO_DEST_REQUEST)).thenReturn(Optional.of(existing));
        integration.getFieldMappings().add(FieldMapping.builder()
                .sourceSchema(existing).targetSchema(existing).sourceFieldPath("/order/id").targetFieldPath("/order/id").build());
        assertThatThrownBy(() -> service.saveSchema(1L, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_DEST_REQUEST).format(PayloadFormat.XML).rawContent("<order><name>Ali</name></order>").build()))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("SCHEMA_MAPPING_CONFLICT");
                });
        assertThat(existing.getRawContent()).isEqualTo("<order><id>42</id></order>");
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void blocksFormatChangeToCsvUntilAffectedMappingsAreRemoved() {
        integration.setSourcePayloadFormat(PayloadFormat.CSV);
        MappingSchema existing = schema(10L);
        existing.setSchemaDefinition(Map.of("type", "object"));
        when(schemaRepository.findByIntegrationIdAndSchemaType(1L, SchemaType.SOURCE_TO_DEST_REQUEST)).thenReturn(Optional.of(existing));
        integration.getFieldMappings().add(FieldMapping.builder()
                .sourceSchema(existing).targetSchema(existing).sourceFieldPath("id").targetFieldPath("id").build());
        assertThatThrownBy(() -> service.saveSchema(1L, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_DEST_REQUEST).format(PayloadFormat.CSV).rawContent("id\n1").build()))
                .isInstanceOf(PayloadValidationException.class);
        assertThat(existing.getFormat()).isEqualTo(PayloadFormat.JSON);
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void replacingUploadedXmlWithPastedXmlClearsTheOldDownloadMetadata() {
        integration.setSourcePayloadFormat(PayloadFormat.XML);
        MappingSchema existing = schema(10L);
        existing.setSampleFile("<old/>".getBytes());
        existing.setFileName("old.xml");
        when(schemaRepository.findByIntegrationIdAndSchemaType(1L, SchemaType.SOURCE_TO_DEST_REQUEST)).thenReturn(Optional.of(existing));
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.saveSchema(1L, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_DEST_REQUEST).format(PayloadFormat.XML).rawContent("<new/>").build());
        assertThat(response.getFileName()).isNull();
        assertThat(response.getSampleDownloadUrl()).isNull();
        assertThat(existing.getSampleFile()).isNull();
    }

    @Test
    void downloadIsScopedToIntegrationAndReturnsOriginalBytes() {

        reset(integrationRepository);
        byte[] bytes = {1, 2, 3};
        MappingSchema existing = schema(10L);
        existing.setSampleFile(bytes);
        existing.setFileName("orders.xlsx");
        existing.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(schemaRepository.findByIdAndIntegrationId(10L, 1L)).thenReturn(Optional.of(existing));
        var file = service.getSampleFile(1L, 10L);
        assertThat(file.bytes()).isEqualTo(bytes);
        assertThatThrownBy(() -> service.getSampleFile(2L, 10L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updatingCurrentFlowSchemaDiscardsObsoleteLinksAndPreservesCurrentLinks() {
        integration.setHasMiddleware(true);
        MappingSchema obsolete = schema(9L);
        obsolete.setSchemaType(SchemaType.SOURCE_TO_DEST_REQUEST);
        MappingSchema existing = schema(10L);
        existing.setSchemaType(SchemaType.SOURCE_TO_MW_REQUEST);
        existing.setFormat(PayloadFormat.XML);
        existing.setRawContent("<order><id>42</id></order>");
        existing.setSchemaDefinition(PayloadSchemas.xmlDefinition(existing.getRawContent(), false));
        MappingSchema target = schema(20L);
        target.setSchemaType(SchemaType.MW_TO_DEST_REQUEST);
        target.setSchemaDefinition(Map.of("type", "object"));
        integration.getMappingSchemas().addAll(List.of(obsolete, existing, target));
        FieldMapping retained = FieldMapping.builder().sourceSchema(existing).targetSchema(target)
                .sourceFieldPath("/order/id").targetFieldPath("id").build();
        integration.getFieldMappings().addAll(List.of(

                FieldMapping.builder().sourceSchema(existing).targetSchema(obsolete)
                        .sourceFieldPath("/order/removed").targetFieldPath("id").build(),
                FieldMapping.builder().sourceSchema(obsolete).targetSchema(existing)
                        .sourceFieldPath("id").targetFieldPath("/order/removed").build(),
                retained));
        when(schemaRepository.findByIntegrationIdAndSchemaType(1L, SchemaType.SOURCE_TO_MW_REQUEST))
                .thenReturn(Optional.of(existing));
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertThat(integration.getFieldMappings()).containsExactly(retained);
            assertThat(integration.getMappingSchemas()).contains(obsolete);
            return null;
        }).when(fieldMappingRepository).flush();

        var response = service.saveSchema(1L, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_MW_REQUEST).format(PayloadFormat.XML)
                .rawContent("<order><id>99</id></order>").build());

        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(integration.getMappingSchemas()).containsExactly(existing, target);
        assertThat(integration.getFieldMappings()).containsExactly(retained);
        verify(fieldMappingRepository).flush();
    }

    @Test
    void invalidNewContentDoesNotRemoveObsoleteSchemasOrMappings() {
        integration.setHasMiddleware(true);
        MappingSchema obsolete = schema(9L);
        obsolete.setSchemaType(SchemaType.SOURCE_TO_DEST_REQUEST);
        integration.getMappingSchemas().add(obsolete);
        FieldMapping link = FieldMapping.builder().sourceSchema(obsolete).targetSchema(obsolete).build();
        integration.getFieldMappings().add(link);

        assertThatThrownBy(() -> service.saveSchema(1L, MappingSchemaRequestDTO.builder()
                .schemaType(SchemaType.SOURCE_TO_MW_REQUEST).format(PayloadFormat.XML)
                .rawContent("<broken>").build())).isInstanceOf(PayloadValidationException.class);

        assertThat(integration.getMappingSchemas()).containsExactly(obsolete);
        assertThat(integration.getFieldMappings()).containsExactly(link);
        verifyNoInteractions(schemaRepository, fieldMappingRepository);
    }

    private MappingSchema schema(Long id) {
        return MappingSchema.builder().id(id).integration(integration).build();
    }

    private FieldMappingRequestDTO request(Long sourceSystemId, Long targetId) {
        return FieldMappingRequestDTO.builder()
                .sourceSchemaId(sourceSystemId).targetSchemaId(targetId)
                .sourceFieldPath("customer.id").targetFieldPath("client.id")
                .build();
    }
}
