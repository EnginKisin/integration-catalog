package com.company.integration_catalog.ai;

import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.payload.*;
import com.company.integration_catalog.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MixedFormatMappingTests {
    record Flow(PayloadFormat source,PayloadFormat target,boolean middleware,boolean response) {}
    static Stream<Flow> flows() {
        return Stream.of(PayloadFormat.JSON,PayloadFormat.XML,PayloadFormat.IDOC).flatMap(source ->
                Stream.of(PayloadFormat.JSON,PayloadFormat.XML,PayloadFormat.IDOC).flatMap(target ->
                        Stream.of(false,true).flatMap(middleware -> Stream.of(false,true)
                                .map(response -> new Flow(source,target,middleware,response)))));
    }

    @ParameterizedTest @MethodSource("flows")
    void allNinePairsSupportDirectAndMiddlewareRequestsAndResponses(Flow flow) {
        Integration integration=Integration.builder().id(1L).hasMiddleware(flow.middleware()).isSync(true)
                .sourcePayloadFormat(flow.source()).destinationPayloadFormat(flow.target()).status(IntegrationStatus.COMPLETED).build();
        PayloadFormat input=flow.response()?flow.target():flow.source(), output=flow.response()?flow.source():flow.target();
        SchemaType sourceType=flow.middleware()?(flow.response()?SchemaType.DEST_TO_MW_RESPONSE:SchemaType.SOURCE_TO_MW_REQUEST)
                :(flow.response()?SchemaType.DEST_TO_SOURCE_RESPONSE:SchemaType.SOURCE_TO_DEST_REQUEST);
        MappingSchemaRequestDTO sourceRequest=request(sourceType,input,true);
        if (!flow.middleware()) {
            sourceRequest.setTargetFormat(output);
            if(output==PayloadFormat.JSON) sourceRequest.setTargetSamplePayload(jsonSample(false));
            else sourceRequest.setTargetRawContent(xml(output,false));
        }
        MappingSchema source=PayloadSchemas.prepare(integration,sourceRequest,null);
        source.setId(10L); integration.getMappingSchemas().add(source);
        MappingSchema target=source;
        if(flow.middleware()) {
            SchemaType targetType=flow.response()?SchemaType.MW_TO_SOURCE_RESPONSE:SchemaType.MW_TO_DEST_REQUEST;
            target=PayloadSchemas.prepare(integration,request(targetType,output,false),null);
            target.setId(20L); integration.getMappingSchemas().add(target);
        }
        PayloadSchemas.validateTarget(integration,source);
        var sourceFields=MappingFields.fields(source,false,100);
        var targetFields=MappingFields.fields(target,!flow.middleware(),100);
        String sourcePath=sourceFields.stream().filter(field -> field.sampleValues().contains("000123")).findFirst().orElseThrow().path();
        String targetPath=targetFields.stream().filter(field -> field.sampleValues().contains("000123")).findFirst().orElseThrow().path();
        PayloadSchemas.validateMappingPath(source,sourcePath);
        PayloadSchemas.validateMappingPath(target,targetPath,!flow.middleware());

        IntegrationRepository integrations=mock(IntegrationRepository.class);
        FieldMappingRepository fields=mock(FieldMappingRepository.class);
        AiJobRepository jobs=mock(AiJobRepository.class);
        when(integrations.findByIdForUpdate(1L)).thenReturn(Optional.of(integration));
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        when(fields.saveAll(anyList())).thenAnswer(call -> {
            List<FieldMapping> added=call.getArgument(0); added.getFirst().setId(100L); return added;
        });
        MappingSuggestionProvider provider=mock(MappingSuggestionProvider.class);
        when(provider.name()).thenReturn("ollama");
        AiProperties properties=AiTestProperties.defaults(); properties.setEnabled(true);
        ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        AiMappingService service=new AiMappingService(integrations,fields,jobs,mock(MappingAppendReceiptRepository.class),
                new IntegrationMapper(),json,properties,List.of(provider),Clock.systemUTC());
        String revision=service.context(1L).contextRevision();
        service.create(1L,"editor","create",new CreateJob(source.getId(),target.getId(),revision,List.of(),null));
        var captor=org.mockito.ArgumentCaptor.forClass(AiJob.class); verify(jobs).save(captor.capture());
        Input snapshot=json.convertValue(captor.getValue().getInputSnapshot(),Input.class);
        assertThat(snapshot.sourceFormat()).isEqualTo(input); assertThat(snapshot.targetFormat()).isEqualTo(output);
        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.COMPLETED);
        var saved=service.append(1L,"editor","append",new AppendRequest(revision,null,List.of(
                new AppendMapping(null,source.getId(),sourcePath,target.getId(),targetPath))));
        assertThat(saved.addedMappings().getFirst().getSourceFieldPath()).isEqualTo(sourcePath);
        assertThat(saved.addedMappings().getFirst().getTargetFieldPath()).isEqualTo(targetPath);
    }

    @Test void normalizesNamespacedAttributesRepeatedElementsAndTextWithoutGuessingNumericTypes() {
        String raw="<o:Order xmlns:o=\"https://example.test/orders\" id=\"000123\"><o:Line><o:Code>A</o:Code></o:Line>"
                +"<o:Line><o:Code>B</o:Code></o:Line><o:Name lang=\"tr\">Deniz</o:Name></o:Order>";
        MappingSchema schema=MappingSchema.builder().format(PayloadFormat.XML).rawContent(raw).build();
        var fields=MappingFields.fields(schema,false,100);
        assertThat(fields).filteredOn(field -> field.path().endsWith("/@id")).singleElement().satisfies(field -> {
            assertThat(field.sampleValues()).containsExactly("000123"); assertThat(field.type()).isEqualTo("unknown");
        });
        assertThat(fields).filteredOn(field -> field.path().endsWith("}Code")).singleElement().satisfies(field -> {
            assertThat(field.path()).contains("{https://example.test/orders}");
            assertThat(field.array()).isTrue(); assertThat(field.sampleValues()).containsExactly("A","B");
        });
        assertThat(fields).filteredOn(field -> field.path().endsWith("}Name")).singleElement()
                .satisfies(field -> assertThat(field.sampleValues()).containsExactly("Deniz"));
        assertThat(fields).filteredOn(field -> field.path().endsWith("/@lang")).hasSize(1);
        assertThatThrownBy(() -> MappingFields.fields(schema,false,1)).isInstanceOf(PayloadValidationException.class);
    }

    @Test void targetFormatAndXmlInputCannotBypassValidationOrResolveExternalResources() {
        Integration integration=Integration.builder().hasMiddleware(false).sourcePayloadFormat(PayloadFormat.JSON)
                .destinationPayloadFormat(PayloadFormat.IDOC).build();
        var request=request(SchemaType.SOURCE_TO_DEST_REQUEST,PayloadFormat.JSON,true);
        request.setTargetFormat(PayloadFormat.XML); request.setTargetRawContent("<Target/>");
        assertCode(() -> PayloadSchemas.prepare(integration,request,null),"FORMAT_MISMATCH");
        request.setTargetFormat(PayloadFormat.IDOC);
        assertCode(() -> PayloadSchemas.prepare(integration,request,null),"INVALID_IDOC");
        request.setTargetRawContent("<!DOCTYPE IDOC [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><IDOC>&secret;</IDOC>");
        assertCode(() -> PayloadSchemas.prepare(integration,request,null),"INVALID_XML");
        request.setTargetRawContent("<IDOC><broken></IDOC>");
        assertCode(() -> PayloadSchemas.prepare(integration,request,null),"INVALID_XML");
        request.setTargetRawContent(xml(PayloadFormat.IDOC,false));
        MappingSchema prepared=PayloadSchemas.prepare(integration,request,null);
        assertThatThrownBy(() -> PayloadSchemas.validateMappingPath(prepared,"/invented",true)).isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> MappingFields.fields(MappingSchema.builder().format(PayloadFormat.CSV).build(),false,100))
                .isInstanceOf(PayloadValidationException.class);
    }

    static MappingSchemaRequestDTO request(SchemaType type,PayloadFormat format,boolean source) {
        var request=MappingSchemaRequestDTO.builder().schemaType(type).format(format).build();
        if(format==PayloadFormat.JSON) request.setSamplePayload(jsonSample(source));
        else request.setRawContent(xml(format,source));
        return request;
    }
    static Map<String,Object> jsonSample(boolean source) {
        return source?Map.of("customerCode","000123","fullName","Deniz"):Map.of("clientId","000123","displayName","Deniz");
    }
    static String xml(PayloadFormat format,boolean source) {
        if(format==PayloadFormat.IDOC) return "<DEBMAS07><IDOC BEGIN=\"1\"><E1KNA1M SEGMENT=\"1\"><KUNNR>000123</KUNNR>"
                +"<NAME1>Deniz</NAME1></E1KNA1M></IDOC></DEBMAS07>";
        return source?"<Source><customerCode>000123</customerCode><fullName>Deniz</fullName></Source>"
                :"<Target><clientId>000123</clientId><displayName>Deniz</displayName></Target>";
    }
    private void assertCode(Runnable action,String code) { assertThatThrownBy(action::run).isInstanceOfSatisfying(PayloadValidationException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(code)); }
}
