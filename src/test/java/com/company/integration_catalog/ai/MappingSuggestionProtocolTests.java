package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.payload.MappingField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.assertj.core.api.Assertions.*;

class MappingSuggestionProtocolTests {
    private final ObjectMapper json=new ObjectMapper();
    private final OllamaMappingSuggestionProvider provider=new OllamaMappingSuggestionProvider(new AiProperties(),json);

    @ParameterizedTest
    @CsvSource({"JSON,JSON","JSON,XML","JSON,IDOC","XML,JSON","XML,XML","XML,IDOC","IDOC,JSON","IDOC,XML","IDOC,IDOC"})
    void compactIdsRestoreExactPublicPathsForEveryFormatPair(PayloadFormat source,PayloadFormat target) throws Exception {
        Input input=new Input(11L,12L,"revision",List.of(field(path(source),source)),
                List.of(field(path(target),target)),List.of(),source,target);
        MappingSuggestionProtocol protocol=new MappingSuggestionProtocol(input);
        assertThat(protocol.evidence()).containsEntry("sourceFormat",source).containsEntry("targetFormat",target);
        var result=provider.validate(input,protocol.expand(json.readTree("""
                {"matches":[{"source":"S0","target":"T0","confidence":"HIGH","reason":"Aynı müşteri kimliği"}],"unmatched":[]}
                """),json));
        assertThat(result.suggestions()).singleElement().satisfies(item -> {
            assertThat(item.sourceSchemaId()).isEqualTo(11L);
            assertThat(item.targetSchemaId()).isEqualTo(12L);
            assertThat(item.sourceFieldPath()).isEqualTo(path(source));
            assertThat(item.targetFieldPath()).isEqualTo(path(target));
        });
    }

    @Test void protectedTargetsAreExcludedFromModelEvidenceAndCannotBeInventedBack() throws Exception {
        Input input=new Input(11L,12L,"revision",List.of(field("/code",PayloadFormat.JSON)),
                List.of(field("/clientId",PayloadFormat.JSON),field("/otherId",PayloadFormat.JSON)),
                List.of(new Connection(11L,"/code",12L,"/clientId")));
        var protocol=new MappingSuggestionProtocol(input);
        String evidence=json.writeValueAsString(protocol.evidence());
        assertThat(evidence).doesNotContain("/clientId").contains("/otherId");
        var compact=json.readTree("""
                {"matches":[{"source":"S0","target":"T0","confidence":"HIGH","reason":"Aynı kimlik"}],"unmatched":[]}
                """);
        assertThat(provider.validate(input,protocol.expand(compact,json)).suggestions())
                .extracting(Suggestion::targetFieldPath).containsExactly("/otherId");
        ((com.fasterxml.jackson.databind.node.ObjectNode)compact.path("matches").get(0)).put("target","T1");
        assertThatThrownBy(() -> protocol.expand(compact,json)).isInstanceOfSatisfying(PayloadValidationException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("AI_INVALID_OUTPUT"));
    }

    @Test void competingSourcesAreWithheldAndExplainedRatherThanChosenArbitrarily() throws Exception {
        Input input=twoByTwo();
        var compact=json.readTree("""
                {"matches":[
                    {"source":"S0","target":"T0","confidence":"HIGH","reason":"Olası kimlik"},
                    {"source":"S1","target":"T0","confidence":"MEDIUM","reason":"Olası kimlik"}],"unmatched":[]}
                """);
        var result=provider.validate(input,new MappingSuggestionProtocol(input).expand(compact,json));
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.unmatchedTargetFields()).filteredOn(item -> item.fieldPath().equals("/clientId"))
                .singleElement().satisfies(item -> assertThat(item.code()).isEqualTo("AMBIGUOUS"));
        assertThat(result.warnings()).hasSize(1);
    }

    @Test void repeatedSameConnectionIsDeduplicatedWithConservativeConfidence() throws Exception {
        Input input=twoByTwo();
        var compact=json.readTree("""
                {"matches":[
                    {"source":"S0","target":"T0","confidence":"HIGH","reason":"Aynı kimlik"},
                    {"source":"S0","target":"T0","confidence":"MEDIUM","reason":"Aynı kimlik"}],"unmatched":[]}
                """);
        var result=provider.validate(input,new MappingSuggestionProtocol(input).expand(compact,json));
        assertThat(result.suggestions()).singleElement().satisfies(item -> assertThat(item.confidence()).isEqualTo(Confidence.MEDIUM));
    }

    @Test void unmatchedCodesBecomeReadableDiagnosticsAndUnknownCodesAreRejected() throws Exception {
        Input input=twoByTwo();
        var protocol=new MappingSuggestionProtocol(input);
        var compact=json.readTree("""
                {"matches":[],"unmatched":[{"field":"T0","code":"TRANSFORMATION_REQUIRED"}]}
                """);
        var result=provider.validate(input,protocol.expand(compact,json));
        assertThat(result.unmatchedTargetFields()).filteredOn(item -> item.fieldPath().equals("/clientId"))
                .singleElement().satisfies(item -> {
                    assertThat(item.code()).isEqualTo("TRANSFORMATION_REQUIRED");
                    assertThat(item.reason()).contains("dönüşüm");
                });
        ((com.fasterxml.jackson.databind.node.ObjectNode)compact.path("unmatched").get(0)).put("code","INVENTED");
        assertThatThrownBy(() -> protocol.expand(compact,json)).isInstanceOf(PayloadValidationException.class);
    }

    @Test void typeAndArrayChecksStillRunAfterExpandingCompactModelOutput() throws Exception {
        Input input=new Input(11L,12L,"revision",List.of(new MappingField("/codes","string",false,true,List.of())),
                List.of(field("/clientId",PayloadFormat.JSON)),List.of());
        var compact=json.readTree("""
                {"matches":[{"source":"S0","target":"T0","confidence":"HIGH","reason":"Aynı kimlik"}],"unmatched":[]}
                """);
        var result=provider.validate(input,new MappingSuggestionProtocol(input).expand(compact,json));
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test void idocStructuralAttributesAreNeverModelCandidatesOnEitherSide() throws Exception {
        String business="/DEBMAS07/IDOC/E1KNA1M/KUNNR";
        List<MappingField> fields=List.of(field("/DEBMAS07/IDOC/@BEGIN",PayloadFormat.IDOC),
                field("/DEBMAS07/IDOC/E1KNA1M/@SEGMENT",PayloadFormat.IDOC),field(business,PayloadFormat.IDOC));
        Input input=new Input(11L,12L,"revision",fields,fields,List.of(),PayloadFormat.IDOC,PayloadFormat.IDOC);
        var protocol=new MappingSuggestionProtocol(input);
        assertThat(json.writeValueAsString(protocol.evidence())).doesNotContain("@BEGIN","@SEGMENT");
        var compact=json.readTree("""
                {"matches":[{"source":"S0","target":"T0","confidence":"HIGH","reason":"Aynı müşteri kimliği"}],"unmatched":[]}
                """);
        var result=provider.validate(input,protocol.expand(compact,json));
        assertThat(result.suggestions()).singleElement().satisfies(item -> {
            assertThat(item.sourceFieldPath()).isEqualTo(business);
            assertThat(item.targetFieldPath()).isEqualTo(business);
        });
        assertThat(result.unmatchedTargetFields()).extracting(UnmatchedField::fieldPath)
                .containsExactly("/DEBMAS07/IDOC/@BEGIN","/DEBMAS07/IDOC/E1KNA1M/@SEGMENT");
        assertThat(result.unmatchedSourceFields()).hasSize(2);
    }

    private Input twoByTwo() {
        return new Input(11L,12L,"revision",List.of(field("/code",PayloadFormat.JSON),field("/other",PayloadFormat.JSON)),
                List.of(field("/clientId",PayloadFormat.JSON),field("/otherId",PayloadFormat.JSON)),List.of());
    }
    private MappingField field(String path,PayloadFormat format) {
        return new MappingField(path,format==PayloadFormat.JSON?"string":"unknown",false,false,List.of("000123"));
    }
    private String path(PayloadFormat format) {
        return switch(format) {
            case JSON -> "/customer~1code";
            case XML -> "/{https://example.test/customer}Customer/@code";
            case IDOC -> "/DEBMAS07/IDOC/E1KNA1M/KUNNR";
            default -> throw new IllegalArgumentException("Unsupported AI test format: "+format);
        };
    }
}
