package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.IntegrationService;
import com.company.integration_catalog.service.MappingService;
import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.payload.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="catalog.payload.postgres-test",matches="true")
@SpringBootTest(properties={
        "spring.datasource.url=${catalog.payload.test-db-url:jdbc:postgresql://localhost:5432/integration_payload_test_20261025}",
        "spring.datasource.username=catalog_payload_test","spring.datasource.password=payload-test-only-password",
        "catalog.credentials.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "catalog.bootstrap.username=payload-test-admin","catalog.bootstrap.password=payload-test-admin-password",
        "spring.jpa.show-sql=false","spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "catalog.ai.enabled=false"
})
class AiMappingPostgresTests {
    @Autowired AiMappingService service;
    @Autowired AiJobTransactions worker;
    @Autowired AiProperties properties;
    @Autowired AiJobRepository jobs;
    @Autowired MappingAppendReceiptRepository receipts;
    @Autowired IntegrationRepository integrations;
    @Autowired MappingSchemaRepository schemas;
    @Autowired FieldMappingRepository fields;
    @Autowired SystemRepository systems;
    @Autowired IntegrationService integrationService;
    @Autowired MappingService mappingService;
    @Autowired PlatformTransactionManager transactions;

    @Test void committedJobResultAppendReplayExpiryAndAggregateDeletionRoundtrip() {
        Fixture fixture=fixture();
        properties.setEnabled(true);
        try {
            Context context=service.context(fixture.integrationId());
            CreateJob request=new CreateJob(fixture.schemaId(),fixture.schemaId(),context.contextRevision(),List.of(),null);
            JobView queued=service.create(fixture.integrationId(),"pg-editor","create",request);
            assertThat(service.create(fixture.integrationId(),"pg-editor","create",request).jobId()).isEqualTo(queued.jobId());
            var claimed=worker.claim(); assertThat(claimed.id()).isEqualTo(queued.jobId());
            assertThat(claimed.input().protectedMappings()).singleElement().satisfies(mapping -> assertThat(mapping.targetFieldPath()).isEqualTo("/email"));
            worker.finish(queued.jobId(),new Suggestions(List.of(new Suggestion("s1",fixture.schemaId(),"/customerCode",
                    fixture.schemaId(),"/clientId",Confidence.HIGH,"Müşteri kodu")),List.of(),List.of(),List.of()),null,null);
            assertThat(service.result(fixture.integrationId(),queued.jobId()).stale()).isFalse();
            assertThat(jobs.findById(queued.jobId()).orElseThrow().getInputSnapshot()).isNull();
            AppendRequest append=new AppendRequest(context.contextRevision(),queued.jobId(),List.of(
                    new AppendMapping("s1",fixture.schemaId(),"/customerCode",fixture.schemaId(),"/clientId")));
            AppendResponse saved=service.append(fixture.integrationId(),"pg-editor","save",append);
            assertThat(saved.addedMappings()).singleElement().satisfies(mapping -> assertThat(mapping.getOrigin()).isEqualTo("AI_ACCEPTED"));
            assertThat(service.append(fixture.integrationId(),"pg-editor","save",append).addedMappings().getFirst().getId())
                    .isEqualTo(saved.addedMappings().getFirst().getId());
            assertThat(fields.findByIntegrationId(fixture.integrationId())).hasSize(2)
                    .extracting(FieldMapping::getId).contains(fixture.manualMappingId());
            assertThat(service.result(fixture.integrationId(),queued.jobId()).stale()).isTrue();
            assertThatThrownBy(() -> service.append(fixture.integrationId(),"pg-editor","save-again",append))
                    .isInstanceOfSatisfying(PayloadValidationException.class,ex -> assertThat(ex.getCode()).isEqualTo("CONTEXT_CHANGED"));
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                AiJob job=jobs.findById(queued.jobId()).orElseThrow(); job.setExpiresAt(Instant.now().minusSeconds(1)); jobs.save(job);
            });
            worker.cleanup();
            assertThat(jobs.findById(queued.jobId()).orElseThrow().getResult()).isNull();
            assertThatThrownBy(() -> service.result(fixture.integrationId(),queued.jobId()))
                    .isInstanceOfSatisfying(PayloadValidationException.class,ex -> assertThat(ex.getStatus()).isEqualTo(410));

            var fullList=mappingService.getFieldMappingsByIntegration(fixture.integrationId()).stream().map(mapping ->
                    FieldMappingRequestDTO.builder().sourceSchemaId(mapping.getSourceSchemaId()).targetSchemaId(mapping.getTargetSchemaId())
                            .sourceFieldPath(mapping.getSourceFieldPath()).targetFieldPath(mapping.getTargetFieldPath())
                            .isAiGenerated(false).build()).toList();
            mappingService.saveFieldMappings(fixture.integrationId(),fullList);
            assertThat(fields.findByIntegrationId(fixture.integrationId())).extracting(FieldMapping::getOrigin).contains("AI_ACCEPTED");
            mappingService.saveFieldMappings(fixture.integrationId(),List.of());
            assertThat(fields.findByIntegrationId(fixture.integrationId())).isEmpty();
            integrationService.deleteIntegration(fixture.integrationId());
            assertThat(jobs.findById(queued.jobId())).isEmpty();
            assertThat(receipts.findByIntegrationIdAndCreatedByAndIdempotencyKey(fixture.integrationId(),"pg-editor","save")).isEmpty();
        } finally { properties.setEnabled(false); cleanup(fixture); }
    }

    @Test void invalidBatchRollsBackAndManualAppendDoesNotRequireAi() {
        Fixture fixture=fixture();
        try {
            String revision=service.context(fixture.integrationId()).contextRevision();
            assertThatThrownBy(() -> service.append(fixture.integrationId(),"pg-editor","invalid",new AppendRequest(revision,null,List.of(
                    new AppendMapping(null,fixture.schemaId(),"/customerCode",fixture.schemaId(),"/clientId"),
                    new AppendMapping(null,fixture.schemaId(),"/missing",fixture.schemaId(),"/email")))))
                    .isInstanceOf(PayloadValidationException.class);
            assertThat(fields.findByIntegrationId(fixture.integrationId())).hasSize(1);
            assertThat(service.context(fixture.integrationId()).contextRevision()).isEqualTo(revision);
            var result=service.append(fixture.integrationId(),"pg-editor","manual",new AppendRequest(revision,null,
                    List.of(new AppendMapping(null,fixture.schemaId(),"/customerCode",fixture.schemaId(),"/clientId"))));
            assertThat(result.addedMappings().getFirst().getOrigin()).isEqualTo("MANUAL");
        } finally { cleanup(fixture); }
    }

    static java.util.stream.Stream<Arguments> formats() {
        return java.util.stream.Stream.of(PayloadFormat.JSON,PayloadFormat.XML,PayloadFormat.IDOC).flatMap(source ->
                java.util.stream.Stream.of(PayloadFormat.JSON,PayloadFormat.XML,PayloadFormat.IDOC).map(target -> Arguments.of(source,target)));
    }

    @ParameterizedTest @MethodSource("formats")
    void allFormatPairsRoundtripQueueApprovalCompletionAndSampleChanges(PayloadFormat sourceFormat,PayloadFormat targetFormat) {
        Fixture fixture=mixedFixture(sourceFormat,targetFormat);
        properties.setEnabled(true);
        try {
            String revision=service.context(fixture.integrationId()).contextRevision();
            var queued=service.create(fixture.integrationId(),"pg-editor","mixed-job",new CreateJob(
                    fixture.schemaId(),fixture.schemaId(),revision,List.of(),null));
            var claimed=worker.claim();
            assertThat(claimed.id()).isEqualTo(queued.jobId());
            assertThat(claimed.input().sourceFormat()).isEqualTo(sourceFormat);
            assertThat(claimed.input().targetFormat()).isEqualTo(targetFormat);
            String from=claimed.input().sourceFields().stream().filter(field -> field.sampleValues().contains("000123"))
                    .findFirst().orElseThrow().path();
            String to=claimed.input().targetFields().stream().filter(field -> field.sampleValues().contains("000123"))
                    .findFirst().orElseThrow().path();
            worker.finish(queued.jobId(),new Suggestions(List.of(new Suggestion("mixed-suggestion",fixture.schemaId(),from,
                    fixture.schemaId(),to,Confidence.HIGH,"Aynı müşteri kodu")),List.of(),List.of(),List.of()),null,null);
            assertThat(service.result(fixture.integrationId(),queued.jobId()).stale()).isFalse();
            var accepted=service.append(fixture.integrationId(),"pg-editor","mixed-save",new AppendRequest(revision,queued.jobId(),
                    List.of(new AppendMapping("mixed-suggestion",fixture.schemaId(),from,fixture.schemaId(),to))));
            assertThat(accepted.addedMappings().getFirst().getOrigin()).isEqualTo("AI_ACCEPTED");
            integrationService.completeIntegration(fixture.integrationId());
            assertThat(integrations.findById(fixture.integrationId()).orElseThrow().getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
            var changed=MixedFormatMappingTests.request(SchemaType.SOURCE_TO_DEST_REQUEST,sourceFormat,true);
            if(sourceFormat==PayloadFormat.JSON) changed.setSamplePayload(Map.of("customerCode","000999","fullName","Deniz"));
            else changed.setRawContent(changed.getRawContent().replace("000123","000999"));
            var saved=mappingService.saveSchema(fixture.integrationId(),changed);
            assertThat(saved.getTargetFormat()).isEqualTo(targetFormat);
            assertThat(service.context(fixture.integrationId()).contextRevision()).isNotEqualTo(accepted.contextRevision());
            assertThat(service.result(fixture.integrationId(),queued.jobId()).stale()).isTrue();
        } finally { properties.setEnabled(false); cleanup(fixture); }
    }

    private Fixture mixedFixture(PayloadFormat sourceFormat,PayloadFormat targetFormat) {
        return new TransactionTemplate(transactions).execute(status -> {
            CatalogSystem source=systems.save(CatalogSystem.builder().name("Mixed source").description("").build());
            CatalogSystem target=systems.save(CatalogSystem.builder().name("Mixed target").description("").build());
            Integration integration=integrations.save(Integration.builder().name("Mixed PG test").description("")
                    .sourceSystem(source).destinationSystem(target).hasMiddleware(false).isSync(false)
                    .sourcePayloadFormat(sourceFormat).destinationPayloadFormat(targetFormat).status(IntegrationStatus.DRAFT).build());
            var request=MixedFormatMappingTests.request(SchemaType.SOURCE_TO_DEST_REQUEST,sourceFormat,true);
            request.setTargetFormat(targetFormat);
            if(targetFormat==PayloadFormat.JSON) request.setTargetSamplePayload(MixedFormatMappingTests.jsonSample(false));
            else request.setTargetRawContent(MixedFormatMappingTests.xml(targetFormat,false));
            MappingSchema schema=schemas.save(PayloadSchemas.prepare(integration,request,null));
            integration.getEnvironmentConfigs().add(EnvironmentConfig.builder().integration(integration).configTarget(ConfigTarget.DESTINATION)
                    .environmentType(EnvironmentType.DEV).url("https://example.test").authType(AuthType.NONE).build());
            return new Fixture(integration.getId(),schema.getId(),null,source.getId(),target.getId());
        });
    }

    private record Fixture(Long integrationId,Long schemaId,Long manualMappingId,Long sourceId,Long targetId) {}
    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            CatalogSystem source=systems.save(CatalogSystem.builder().name("AI test source").description("").build());
            CatalogSystem target=systems.save(CatalogSystem.builder().name("AI test target").description("").build());
            Integration integration=integrations.save(Integration.builder().name("AI PG test").description("")
                    .sourceSystem(source).destinationSystem(target).hasMiddleware(false).isSync(false)
                    .sourcePayloadFormat(PayloadFormat.JSON).destinationPayloadFormat(PayloadFormat.JSON).status(IntegrationStatus.DRAFT).build());
            MappingSchema schema=schemas.save(MappingSchema.builder().integration(integration).schemaType(SchemaType.SOURCE_TO_DEST_REQUEST)
                    .schemaDefinition(Map.of("customerCode","C1","email","demo@example.com"))
                    .samplePayload(Map.of("customerCode","C1","email","demo@example.com"))
                    .targetSamplePayload(Map.of("clientId","C1","email","demo@example.com")).build());
            FieldMapping manual=fields.save(FieldMapping.builder().integration(integration).sourceSchema(schema).targetSchema(schema)
                    .sourceFieldPath("/email").targetFieldPath("/email").isAiGenerated(false).origin("MANUAL").build());
            return new Fixture(integration.getId(),schema.getId(),manual.getId(),source.getId(),target.getId());
        });
    }
    private void cleanup(Fixture fixture) {
        if(integrations.existsById(fixture.integrationId())) integrationService.deleteIntegration(fixture.integrationId());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            systems.deleteById(fixture.sourceId()); systems.deleteById(fixture.targetId());
        });
    }
}
