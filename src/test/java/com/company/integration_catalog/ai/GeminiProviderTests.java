package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.payload.MappingField;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.mockito.ArgumentCaptor;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiProviderTests {
    private final ObjectMapper json=new ObjectMapper();
    private final AiProperties properties=AiTestProperties.defaults();
    private final HttpClient client=mock(HttpClient.class);
    private GeminiMappingSuggestionProvider provider;
    private static final String COMPACT="""
            {"matches":[{"source":"S0","target":"T0","confidence":"HIGH","reason":"Aynı müşteri kimliği"}],"unmatched":[]}
            """;

    @BeforeEach void setup() {
        properties.setProvider("gemini"); properties.setModel("gemini-3.5-flash-lite");
        properties.setGeminiApiKey("unit-test-key-only");
        provider=new GeminiMappingSuggestionProvider(properties,json,client);
    }

    @ParameterizedTest
    @CsvSource({"JSON,JSON","JSON,XML","JSON,IDOC","XML,JSON","XML,XML","XML,IDOC","IDOC,JSON","IDOC,XML","IDOC,IDOC"})
    void everyFormatPairUsesTheSamePublicPathsAndValidation(PayloadFormat source,PayloadFormat target) throws Exception {
        respond(200,success(COMPACT));
        var result=provider.suggest(input(source,target));
        assertThat(result.suggestions()).singleElement().satisfies(item -> {
            assertThat(item.sourceSchemaId()).isEqualTo(11L); assertThat(item.targetSchemaId()).isEqualTo(12L);
            assertThat(item.sourceFieldPath()).isEqualTo(path(source));
            assertThat(item.targetFieldPath()).isEqualTo(path(target));
        });
    }

    @Test void keyIsOnlyAHeaderAndRequestUsesOfficialHttpsAndStructuredJson() throws Exception {
        properties.setBaseUrl("http://unrelated.example.test");
        respond(200,success(COMPACT)); provider.suggest(input(PayloadFormat.JSON,PayloadFormat.XML));
        HttpRequest request=capturedRequest();
        assertThat(request.uri().toString()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
                .doesNotContain(properties.getGeminiApiKey());
        assertThat(request.headers().firstValue("x-goog-api-key")).contains(properties.getGeminiApiKey());
        String text=body(request);
        assertThat(text).doesNotContain(properties.getGeminiApiKey(),"revision","protectedMappings");
        JsonNode payload=json.readTree(text);
        assertThat(payload.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(payload.path("generationConfig").path("responseJsonSchema").path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(payload.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(properties.getMaxOutputTokens());
        assertThat(payload.has("tools")).isFalse();
        assertThat(json.readTree(payload.path("contents").get(0).path("parts").get(0).path("text").textValue())
                .path("sourceFields").get(0).path("sampleValues").isEmpty()).isTrue();
    }

    @Test void missingKeyAndInvalidModelFailWithoutNetworkIo() {
        properties.setGeminiApiKey("");
        assertThat(provider.availabilityCode()).isEqualTo("AI_API_KEY_MISSING");
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_API_KEY_MISSING");
        properties.setGeminiApiKey("unit-test-key-only"); properties.setModel("gemini-model?key=leak");
        assertThat(provider.availabilityCode()).isEqualTo("AI_CONFIGURATION_INVALID");
        properties.setModel("qwen3.5:4b");
        assertCode(provider::validateConfiguration,"AI_CONFIGURATION_INVALID");
        verifyNoInteractions(client);
    }

    @Test void capabilitiesCheckModelAccessWithoutGeneratingContent() throws Exception {
        respond(200,json.writeValueAsString(Map.of("name","models/"+properties.getModel(),
                "supportedGenerationMethods",List.of("generateContent","countTokens"))));
        assertThat(provider.availabilityCode()).isEqualTo("READY");
        HttpRequest request=capturedRequest();
        assertThat(request.method()).isEqualTo("GET"); assertThat(request.bodyPublisher()).isEmpty();
        assertThat(request.uri().toString()).endsWith("/models/gemini-3.5-flash-lite");
    }

    @ParameterizedTest
    @CsvSource({"400,AI_CONFIGURATION_INVALID","401,AI_AUTHENTICATION_FAILED","403,AI_AUTHENTICATION_FAILED",
            "404,AI_MODEL_NOT_AVAILABLE","429,AI_RATE_LIMITED","500,AI_UNAVAILABLE","503,AI_UNAVAILABLE","504,AI_TIMEOUT"})
    void upstreamErrorsAreSanitizedAndNotRetried(int status,String code) throws Exception {
        respond(status,"sensitive upstream body with unit-test-key-only and sample values");
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),code);
        verify(client,times(1)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }

    @Test void safetyBlocksAndTokenTruncationNeverBecomeSuccessfulSuggestions() throws Exception {
        respond(200,"{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_CONTENT_BLOCKED");
        respond(200,success(COMPACT).replace("STOP","MAX_TOKENS"));
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_OUTPUT_TRUNCATED");
        respond(200,success(COMPACT).replace("STOP","SAFETY"));
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_CONTENT_BLOCKED");
    }

    @Test void fabricatedIdsAndNonJsonResponsesAreRejected() throws Exception {
        respond(200,success(COMPACT.replace("S0","S999")));
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_INVALID_OUTPUT");
        respond(200,success("```json\n"+COMPACT+"\n```"));
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_INVALID_OUTPUT");
        respond(200,success(COMPACT+" {}"));
        assertCode(() -> provider.suggest(input(PayloadFormat.JSON,PayloadFormat.JSON)),"AI_INVALID_OUTPUT");
    }

    @Test void protectedTargetsSkipTheExternalRequestAndRemainUntouched() {
        Input original=input(PayloadFormat.JSON,PayloadFormat.JSON);
        Input protectedInput=new Input(11L,12L,"revision",original.sourceFields(),original.targetFields(),
                List.of(new Connection(11L,path(PayloadFormat.JSON),12L,path(PayloadFormat.JSON))));
        var result=provider.suggest(protectedInput);
        assertThat(result.suggestions()).isEmpty(); assertThat(result.unmatchedTargetFields()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test void unknownXmlTypesAndIncompatibleArraysUseTheSharedCopyRules() throws Exception {
        respond(200,success(COMPACT));
        Input input=new Input(11L,12L,"revision",List.of(new MappingField("/amount","number",false,true,List.of())),
                List.of(new MappingField("/Order/Amount","unknown",false,false,List.of())),List.of(),PayloadFormat.JSON,PayloadFormat.XML);
        var result=provider.suggest(input);
        assertThat(result.suggestions()).isEmpty(); assertThat(result.warnings()).isNotEmpty();
    }

    private Input input(PayloadFormat source,PayloadFormat target) {
        return new Input(11L,12L,"revision",List.of(field(source)),List.of(field(target)),List.of(),source,target);
    }
    private MappingField field(PayloadFormat format) {
        return new MappingField(path(format),format==PayloadFormat.JSON?"string":"unknown",false,false,List.of());
    }
    private String path(PayloadFormat format) {
        return switch(format) {
            case JSON -> "/customer~1code";
            case XML -> "/{https://example.test/customer}Customer/@code";
            case IDOC -> "/DEBMAS07/IDOC/E1KNA1M/KUNNR";
            default -> throw new IllegalArgumentException();
        };
    }
    private String success(String content) throws Exception {
        return json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP", "content",
                Map.of("parts",List.of(Map.of("text",content)))))));
    }
    @SuppressWarnings("unchecked") private void respond(int status,String body) {
        HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        doReturn(CompletableFuture.completedFuture(response)).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    private HttpRequest capturedRequest() {
        ArgumentCaptor<HttpRequest> request=ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(request.capture(),any(HttpResponse.BodyHandler.class));
        return request.getValue();
    }
    private String body(HttpRequest request) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        CompletableFuture<String> result=new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) { byte[] bytes=new byte[buffer.remaining()]; buffer.get(bytes); output.writeBytes(bytes); }
            @Override public void onError(Throwable error) { result.completeExceptionally(error); }
            @Override public void onComplete() { result.complete(output.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(2,TimeUnit.SECONDS);
    }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(PayloadValidationException.class,ex -> {
            assertThat(ex.getCode()).isEqualTo(code);
            assertThat(com.company.integration_catalog.exception.ErrorCode.values())
                    .extracting(Enum::name).contains(ex.getCode());
            assertThat(ex.getMessage()).doesNotContain("unit-test-key-only","sensitive upstream","sample values");
        });
    }
}
