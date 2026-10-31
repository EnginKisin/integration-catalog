package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiMappingServiceTests {
    IntegrationRepository integrations=mock(IntegrationRepository.class);
    FieldMappingRepository fields=mock(FieldMappingRepository.class);
    AiJobRepository jobs=mock(AiJobRepository.class);
    MappingAppendReceiptRepository receipts=mock(MappingAppendReceiptRepository.class);
    MappingSuggestionProvider provider=mock(MappingSuggestionProvider.class);
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    AiProperties properties=new AiProperties();
    Clock clock=Clock.fixed(Instant.parse("2026-10-30T14:00:00Z"),ZoneOffset.UTC);
    Integration integration;
    MappingSchema schema;
    AiMappingService service;

    @BeforeEach void setup() {
        integration=Integration.builder().id(1L).name("Test").description("")
                .hasMiddleware(false).isSync(false).sourcePayloadFormat(PayloadFormat.JSON)
                .destinationPayloadFormat(PayloadFormat.JSON).status(IntegrationStatus.COMPLETED).build();
        schema=MappingSchema.builder().id(10L).integration(integration).schemaType(SchemaType.SOURCE_TO_DEST_REQUEST)
                .samplePayload(Map.of("id","C1","name","Ada","email","demo@example.com"))
                .schemaDefinition(Map.of("id","C1","name","Ada","email","demo@example.com"))
                .targetSamplePayload(Map.of("customerId","C1","displayName","Ada","label","Ada","email","demo@example.com")).build();
        integration.getMappingSchemas().add(schema);
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        service=new AiMappingService(integrations,fields,jobs,receipts,new IntegrationMapper(),json,properties,List.of(provider),clock);
    }

    @Test void disabledAiDoesNotCallProviderAndContextRemainsAvailable() {
        assertThat(service.capabilities().code()).isEqualTo("AI_DISABLED");
        assertThat(service.context(1L).schemas()).hasSize(1);
        assertThatThrownBy(() -> service.create(1L,"editor","key",request())).isInstanceOfSatisfying(PayloadValidationException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("AI_DISABLED"));
        verifyNoInteractions(provider); verify(jobs,never()).save(any());
    }
    @Test void queueingSnapshotsInputsWithoutChangingExistingMappingsOrStatus() {
        properties.setEnabled(true); when(provider.name()).thenReturn("ollama");
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        var view=service.create(1L,"editor","key",request());
        assertThat(view.status()).isEqualTo("QUEUED");
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
        verifyNoInteractions(fields); verify(provider,never()).suggest(any());
        ArgumentCaptor<AiJob> captor=ArgumentCaptor.forClass(AiJob.class); verify(jobs).save(captor.capture());
        assertThat(captor.getValue().getInputSnapshot()).containsKeys("sourceFields","targetFields","protectedMappings");
    }
    @Test void rejectsStaleContextAndMissingDirectTargetBeforeSavingJob() {
        properties.setEnabled(true); when(provider.name()).thenReturn("ollama");
        var request=request(); schema.setTargetSamplePayload(Map.of("different",1));
        assertCode(() -> service.create(1L,"editor","key",request),"CONTEXT_CHANGED");
        schema.setTargetSamplePayload(null);
        assertCode(() -> service.create(1L,"editor","key",request()),"SAMPLE_REQUIRED");
        verify(jobs,never()).save(any());
    }
    @Test void existingMappingsAndUnsavedUiMappingsAreBothProtected() {
        properties.setEnabled(true); when(provider.name()).thenReturn("ollama"); when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        integration.getFieldMappings().add(link(1L,"/email","/email"));
        CreateJob request=new CreateJob(10L,10L,revision(),List.of(new Connection(10L,"/name",10L,"/displayName")),new Options(false));
        service.create(1L,"editor","key",request);
        ArgumentCaptor<AiJob> captor=ArgumentCaptor.forClass(AiJob.class); verify(jobs).save(captor.capture());
        Input input=json.convertValue(captor.getValue().getInputSnapshot(),Input.class);
        assertThat(input.protectedMappings()).hasSize(2);
        assertThat(input.sourceFields()).allSatisfy(field -> assertThat(field.sampleValues()).isEmpty());
    }
    @Test void appendPreservesExistingIdsAndRecordsAcceptedAndEditedProvenance() {
        integration.getFieldMappings().add(link(1L,"/email","/email"));
        AiJob job=completedJob(); when(jobs.findByIdAndIntegrationId("job",1L)).thenReturn(Optional.of(job));
        AtomicLong nextId=new AtomicLong(100);
        when(fields.saveAll(anyList())).thenAnswer(call -> {
            List<FieldMapping> additions=call.getArgument(0); additions.forEach(mapping -> mapping.setId(nextId.getAndIncrement())); return additions;
        });
        var response=service.append(1L,"editor","save",new AppendRequest(revision(),"job",List.of(
                new AppendMapping("s1",10L,"/id",10L,"/customerId"),
                new AppendMapping("s2",10L,"/name",10L,"/displayName"))));
        assertThat(response.addedMappings()).extracting(item -> item.getOrigin()).containsExactly("AI_ACCEPTED","AI_EDITED");
        assertThat(response.addedMappings()).extracting(item -> item.getIsAiGenerated()).containsExactly(true,false);
        assertThat(integration.getFieldMappings()).hasSize(3);
        assertThat(integration.getFieldMappings().getFirst().getId()).isEqualTo(1L);
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.DRAFT);
        verify(fields,never()).deleteByIntegrationId(any()); verify(receipts).save(any());
    }
    @Test void validatesEntireAppendBeforeWritingAndRejectsOccupiedTargets() {
        integration.getFieldMappings().add(link(1L,"/email","/email"));
        assertCode(() -> service.append(1L,"editor","save",new AppendRequest(revision(),null,List.of(
                new AppendMapping(null,10L,"/id",10L,"/customerId"),
                new AppendMapping(null,10L,"/name",10L,"/email")))),"MAPPING_CONFLICT");
        verifyNoInteractions(fields); verify(receipts,never()).save(any());
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
    }
    @Test void manualAppendWorksWithAiDisabledAndRejectsFabricatedPathsAndProvenance() {
        assertCode(() -> service.append(1L,"editor","save",new AppendRequest(revision(),null,
                List.of(new AppendMapping(null,10L,"/invented",10L,"/customerId")))),"FIELD_PATH_NOT_FOUND");
        assertCode(() -> service.append(1L,"editor","save",new AppendRequest(revision(),null,
                List.of(new AppendMapping("fake",10L,"/id",10L,"/customerId")))),"INVALID_SUGGESTION");
        when(fields.saveAll(anyList())).thenAnswer(call -> { List<FieldMapping> added=call.getArgument(0); added.getFirst().setId(100L); return added; });
        assertThat(service.append(1L,"editor","save",new AppendRequest(revision(),null,
                List.of(new AppendMapping(null,10L,"/id",10L,"/customerId")))).addedMappings().getFirst().getOrigin()).isEqualTo("MANUAL");
    }
    @Test @SuppressWarnings("unchecked") void duplicateAppendReplaysOriginalResponseWithoutWriting() {
        AppendRequest request=new AppendRequest(revision(),null,List.of(new AppendMapping(null,10L,"/id",10L,"/customerId")));
        ArgumentCaptor<MappingAppendReceipt> captor=ArgumentCaptor.forClass(MappingAppendReceipt.class);
        when(fields.saveAll(anyList())).thenAnswer(call -> { List<FieldMapping> added=call.getArgument(0); added.getFirst().setId(100L); return added; });
        AppendResponse first=service.append(1L,"editor","save",request);
        verify(receipts).save(captor.capture());
        when(receipts.findByIntegrationIdAndCreatedByAndIdempotencyKey(1L,"editor","save")).thenReturn(Optional.of(captor.getValue()));
        AppendResponse replay=service.append(1L,"editor","save",request);
        assertThat(replay.contextRevision()).isEqualTo(first.contextRevision());
        assertThat(replay.addedMappings().getFirst().getId()).isEqualTo(100L);
        verify(fields,times(1)).saveAll(anyList());
    }
    @Test void changedProviderDoesNotPreventManualStartupButCannotQueueAi() {
        properties.setEnabled(true); properties.setProvider("gemini");
        when(provider.name()).thenReturn("ollama");
        assertThat(service.capabilities().code()).isEqualTo("AI_PROVIDER_NOT_SUPPORTED");
        assertCode(() -> service.create(1L,"editor","key",request()),"AI_PROVIDER_NOT_SUPPORTED");
    }
    @Test void incompatibleIndependentTargetReturnsUnprocessableEntityWithoutQueuing() {
        properties.setEnabled(true); when(provider.name()).thenReturn("ollama");
        integration.setDestinationPayloadFormat(PayloadFormat.XML);
        assertThatThrownBy(() -> service.create(1L,"editor","key",request()))
                .isInstanceOfSatisfying(PayloadValidationException.class,ex -> {
                    assertThat(ex.getStatus()).isEqualTo(422); assertThat(ex.getCode()).isEqualTo("FORMAT_MISMATCH");
                });
        verify(jobs,never()).save(any());
    }
    @Test void backendSelectsGeminiAndPersistsItsIdentityWithoutCallingTheOtherProvider() {
        MappingSuggestionProvider gemini=mock(MappingSuggestionProvider.class);
        when(provider.name()).thenReturn("ollama"); when(gemini.name()).thenReturn("gemini");
        properties.setEnabled(true); properties.setProvider("gemini"); properties.setModel("gemini-3.5-flash-lite");
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        service=new AiMappingService(integrations,fields,jobs,receipts,new IntegrationMapper(),json,properties,List.of(provider,gemini),clock);
        service.create(1L,"editor","gemini-job",request());
        ArgumentCaptor<AiJob> saved=ArgumentCaptor.forClass(AiJob.class); verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isEqualTo("gemini");
        assertThat(saved.getValue().getModel()).isEqualTo("gemini-3.5-flash-lite");
        verify(gemini).validateConfiguration();
        verify(gemini,never()).availabilityCode(); verify(gemini,never()).suggest(any());
        verify(provider,never()).availabilityCode(); verify(provider,never()).suggest(any());
    }
    @Test void missingGeminiKeyDoesNotQueueOrFallBackToOllamaAndManualContextRemainsAvailable() {
        properties.setEnabled(true); properties.setProvider("gemini"); properties.setModel("gemini-3.5-flash-lite");
        var gemini=new GeminiMappingSuggestionProvider(properties,json);
        when(provider.name()).thenReturn("ollama");
        service=new AiMappingService(integrations,fields,jobs,receipts,new IntegrationMapper(),json,properties,List.of(provider,gemini),clock);
        assertThat(service.capabilities().code()).isEqualTo("AI_API_KEY_MISSING");
        assertCode(() -> service.create(1L,"editor","gemini-job",request()),"AI_API_KEY_MISSING");
        assertThat(service.context(1L).schemas()).hasSize(1);
        verify(jobs,never()).save(any()); verify(provider,never()).suggest(any());
    }

    private CreateJob request() { return new CreateJob(10L,10L,revision(),List.of(),null); }
    private String revision() { return service.context(1L).contextRevision(); }
    private FieldMapping link(Long id,String source,String target) { return FieldMapping.builder().id(id).integration(integration)
            .sourceSchema(schema).targetSchema(schema).sourceFieldPath(source).targetFieldPath(target).isAiGenerated(false).build(); }
    @SuppressWarnings("unchecked") private AiJob completedJob() {
        AiJob job=new AiJob(); job.setId("job"); job.setIntegration(integration); job.setStatus("SUCCEEDED");
        job.setContextRevision(revision()); job.setExpiresAt(clock.instant().plusSeconds(3600));
        job.setResult(json.convertValue(new Suggestions(List.of(
                new Suggestion("s1",10L,"/id",10L,"/customerId",Confidence.HIGH,"Müşteri kodu"),
                new Suggestion("s2",10L,"/name",10L,"/label",Confidence.MEDIUM,"Ad alanı")),List.of(),List.of(),List.of()),Map.class));
        return job;
    }
    private void assertCode(Runnable action,String code) { assertThatThrownBy(action::run).isInstanceOfSatisfying(PayloadValidationException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(code)); }
}
