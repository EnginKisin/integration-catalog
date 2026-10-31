package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.payload.JsonMappingFields;
import com.company.integration_catalog.payload.MappingField;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaProviderTests {
    ObjectMapper json=new ObjectMapper();
    AiProperties properties=new AiProperties();
    OllamaMappingSuggestionProvider provider=new OllamaMappingSuggestionProvider(properties,json);
    Input input=new Input(10L,20L,"revision",
            List.of(field("/customerCode","string",false),field("/amount","number",false)),
            List.of(field("/clientId","string",false),field("/total","number",false)),List.of());

    @Test void validatesConnectionsAndSuppliesExplanationsForEveryUnmatchedField() throws Exception {
        var result=provider.validate(input,json.readTree(output("/customerCode","/clientId")));
        assertThat(result.suggestions()).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.sourceSchemaId()).isEqualTo(10L); assertThat(suggestion.targetSchemaId()).isEqualTo(20L);
            assertThat(suggestion.confidence()).isEqualTo(Confidence.HIGH); assertThat(suggestion.suggestionId()).isNotBlank();
        });
        assertThat(result.unmatchedSourceFields()).extracting(UnmatchedField::fieldPath).containsExactly("/amount");
        assertThat(result.unmatchedTargetFields()).extracting(UnmatchedField::fieldPath).containsExactly("/total");
    }
    @Test void fabricatedPathsAndDuplicateTargetsFailTheWholeResult() throws Exception {
        assertThatThrownBy(() -> provider.validate(input,json.readTree(output("/invented","/clientId"))))
                .isInstanceOfSatisfying(PayloadValidationException.class,ex -> assertThat(ex.getCode()).isEqualTo("AI_INVALID_OUTPUT"));
        var duplicate=json.readTree(output("/customerCode","/clientId"));
        ((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("matches")).add(duplicate.path("matches").get(0));
        assertThatThrownBy(() -> provider.validate(input,duplicate)).isInstanceOf(PayloadValidationException.class);
    }
    @Test void protectedTargetsAndTypeConversionsAreNeverOfferedAsCopyMappings() throws Exception {
        var protectedInput=new Input(10L,20L,"revision",input.sourceFields(),input.targetFields(),
                List.of(new Connection(10L,"/customerCode",20L,"/clientId")));
        var result=provider.validate(protectedInput,json.readTree(output("/customerCode","/clientId")));
        assertThat(result.suggestions()).isEmpty(); assertThat(result.warnings()).isNotEmpty();
        var incompatible=provider.validate(input,json.readTree(output("/amount","/clientId")));
        assertThat(incompatible.suggestions()).isEmpty(); assertThat(incompatible.unmatchedTargetFields()).hasSize(2);
    }
    @Test void zeroMatchesIsASuccessAndMalformedOrLowConfidenceOutputIsRejected() throws Exception {
        assertThat(provider.validate(input,json.readTree("{\"matches\":[],\"unmatchedSourceFields\":[],\"unmatchedTargetFields\":[],\"warnings\":[]}"))
                .suggestions()).isEmpty();
        assertThatThrownBy(() -> provider.validate(input,json.readTree("{}"))).isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> provider.validate(input,json.readTree(output("/customerCode","/clientId").replace("HIGH","LOW"))))
                .isInstanceOf(PayloadValidationException.class);
    }
    @Test void cloudModelsAreRefusedWithoutMakingAnyHttpRequest() {
        HttpClient client=mock(HttpClient.class); properties.setModel("some-model:cloud");
        var local=new OllamaMappingSuggestionProvider(properties,json,client);
        assertThat(local.availabilityCode()).isEqualTo("AI_CLOUD_MODEL_FORBIDDEN");
        assertThatThrownBy(() -> local.suggest(input)).isInstanceOf(PayloadValidationException.class);
        verifyNoInteractions(client);
    }
    @Test void mixedFormatSuggestionsPreserveXmlNamespacesAndIdocPaths() throws Exception {
        String source="/{https://example.test/customer}Customer/@code",target="/DEBMAS07/IDOC/E1KNA1M/KUNNR";
        Input mixed=new Input(10L,20L,"revision",List.of(field(source,"unknown",false)),
                List.of(field(target,"unknown",false)),List.of(),PayloadFormat.XML,PayloadFormat.IDOC);
        var result=provider.validate(mixed,json.readTree(output(source,target)));
        assertThat(result.suggestions()).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.sourceFieldPath()).isEqualTo(source); assertThat(suggestion.targetFieldPath()).isEqualTo(target);
        });
        assertThatThrownBy(() -> provider.validate(mixed,json.readTree(output("/Customer/@code",target))))
                .isInstanceOf(PayloadValidationException.class);
    }
    @Test void undeclaredXmlTypesAcceptScalarNumbersButDoNotEraseArrayBoundaries() throws Exception {
        Input amount=new Input(10L,20L,"revision",List.of(field("/amount","number",false)),
                List.of(field("/Order/NetAmount","unknown",false)),List.of(),PayloadFormat.JSON,PayloadFormat.XML);
        assertThat(provider.validate(amount,json.readTree(output("/amount","/Order/NetAmount"))).suggestions()).hasSize(1);
        Input repeated=new Input(10L,20L,"revision",List.of(field("/IDOC/ITEM/CODE","unknown",true)),
                List.of(field("/sku","string",false)),List.of(),PayloadFormat.IDOC,PayloadFormat.JSON);
        assertThat(provider.validate(repeated,json.readTree(output("/IDOC/ITEM/CODE","/sku"))).suggestions()).isEmpty();
    }
    @Test @SuppressWarnings("unchecked") void providerTimeoutIsSanitizedAndTruncatedOutputFails() throws Exception {
        HttpClient client=mock(HttpClient.class);
        doReturn(CompletableFuture.failedFuture(new HttpTimeoutException("sensitive-provider-details")))
                .when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var local=new OllamaMappingSuggestionProvider(properties,json,client);
        assertThatThrownBy(() -> local.suggest(input)).isInstanceOfSatisfying(PayloadValidationException.class,ex -> {
            assertThat(ex.getCode()).isEqualTo("AI_TIMEOUT"); assertThat(ex.getMessage()).doesNotContain("sensitive");
        });
        HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"done\":true,\"done_reason\":\"length\",\"message\":{\"content\":\"{}\"}}".getBytes());
        doReturn(CompletableFuture.completedFuture(response)).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> local.suggest(input)).isInstanceOfSatisfying(PayloadValidationException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("AI_OUTPUT_TRUNCATED"));
    }
    @Test @SuppressWarnings("unchecked") void compactHttpResponseBecomesTheExistingPublicContract() throws Exception {
        HttpClient client=mock(HttpClient.class);
        HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String compact="""
                {"matches":[{"source":"S1","target":"T0","confidence":"HIGH","reason":"Aynı müşteri kimliği"}],
                 "unmatched":[{"field":"T1","code":"INSUFFICIENT_CONTEXT"}]}
                """;
        when(response.body()).thenReturn(json.writeValueAsBytes(Map.of("done",true,"done_reason","stop",
                "eval_count",64,"message",Map.of("content",compact))));
        doReturn(CompletableFuture.completedFuture(response)).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var result=new OllamaMappingSuggestionProvider(properties,json,client).suggest(input);
        assertThat(result.suggestions()).singleElement().satisfies(item -> {
            assertThat(item.sourceFieldPath()).isEqualTo("/customerCode");
            assertThat(item.targetFieldPath()).isEqualTo("/clientId");
        });
        assertThat(result.unmatchedTargetFields()).singleElement().satisfies(item -> {
            assertThat(item.fieldPath()).isEqualTo("/total");
            assertThat(item.code()).isEqualTo("INSUFFICIENT_CONTEXT");
        });
    }
    @Test void allProtectedTargetsSkipInferenceWithoutChangingExistingConnections() {
        HttpClient client=mock(HttpClient.class);
        Input protectedInput=new Input(10L,20L,"revision",input.sourceFields(),input.targetFields(),List.of(
                new Connection(10L,"/customerCode",20L,"/clientId"),new Connection(10L,"/amount",20L,"/total")));
        var result=new OllamaMappingSuggestionProvider(properties,json,client).suggest(protectedInput);
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.unmatchedSourceFields()).isEmpty();
        assertThat(result.unmatchedTargetFields()).isEmpty();
        verifyNoInteractions(client);
    }
    @Test void idocSourceContainingOnlyStructuralControlsSkipsInference() {
        HttpClient client=mock(HttpClient.class);
        Input structuralInput=new Input(10L,20L,"revision",List.of(field("/IDOC/@BEGIN","unknown",false)),
                input.targetFields(),List.of(),PayloadFormat.IDOC,PayloadFormat.JSON);
        var result=new OllamaMappingSuggestionProvider(properties,json,client).suggest(structuralInput);
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.unmatchedSourceFields()).hasSize(1);
        assertThat(result.unmatchedTargetFields()).hasSize(2);
        verifyNoInteractions(client);
    }
    private MappingField field(String path,String type,boolean array) { return new MappingField(path,type,false,array,List.of()); }
    private String output(String source,String target) throws Exception {
        return json.writeValueAsString(Map.of("matches",List.of(Map.of("sourcePath",source,"targetPath",target,
                "confidence","HIGH","reason","Alanlar aynı müşteri kimliğini ifade ediyor.")),
                "unmatchedSourceFields",List.of(),"unmatchedTargetFields",List.of(),"warnings",List.of()));
    }
}
