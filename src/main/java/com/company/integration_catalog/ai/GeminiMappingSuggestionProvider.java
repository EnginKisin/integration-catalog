package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static com.company.integration_catalog.ai.AiMappingDtos.*;
import static com.company.integration_catalog.ai.AiProviderHttpClient.error;

@Component
public class GeminiMappingSuggestionProvider implements MappingSuggestionProvider {
    private static final String API="https://generativelanguage.googleapis.com/v1beta/models/";
    private final AiProperties properties;
    private final ObjectMapper json;
    private final AiProviderHttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiMappingSuggestionProvider(AiProperties properties,ObjectMapper json) {
        this(properties,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build());
    }

    GeminiMappingSuggestionProvider(AiProperties properties,ObjectMapper json,HttpClient client) {
        this.properties=properties;
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(262144).build());
        this.http=new AiProviderHttpClient(this.json,client);
    }

    @Override public String name() { return "gemini"; }

    @Override public void validateConfiguration() {
        String key=properties.getGeminiApiKey();
        if (key==null || key.isBlank()) throw error("AI_API_KEY_MISSING","Gemini API key is not configured");
        if (key.chars().anyMatch(c -> c<=32 || c>=127)
                || !properties.getModel().matches("gemini-[A-Za-z0-9][A-Za-z0-9._-]*"))
            throw error("AI_CONFIGURATION_INVALID","Invalid Gemini configuration; use a bare Gemini model ID");
    }

    @Override public String availabilityCode() {
        try {
            validateConfiguration();
            JsonNode model=http.exchange(request("",3).GET().build(),this::httpError);
            if (!model.path("name").asText().equals("models/"+properties.getModel())) return "AI_MODEL_NOT_AVAILABLE";
            for (JsonNode method:model.path("supportedGenerationMethods"))
                if ("generateContent".equals(method.asText())) return "READY";
            return "AI_MODEL_NOT_AVAILABLE";
        } catch (PayloadValidationException ex) { return ex.getCode(); }
    }

    @Override public Suggestions suggest(Input input) {
        validateConfiguration();
        MappingSuggestionProtocol protocol=new MappingSuggestionProtocol(input);
        if (!protocol.hasCandidates()) {
            var empty=json.createObjectNode(); empty.putArray("matches"); empty.putArray("unmatched");
            return decode(input,protocol,empty);
        }
        Map<String,Object> body=Map.of(
                "systemInstruction",Map.of("parts",List.of(Map.of("text",MappingSuggestionProtocol.SYSTEM))),
                "contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",encode(protocol.evidence()))))),
                "generationConfig",Map.of("responseMimeType","application/json","responseJsonSchema",protocol.schema(),
                        "candidateCount",1,"maxOutputTokens",properties.getMaxOutputTokens(),"temperature",0.2));
        HttpRequest request=request(":generateContent",properties.getRequestTimeoutSeconds())
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(encode(body))).build();
        JsonNode response=http.exchange(request,this::httpError);
        String blocked=response.path("promptFeedback").path("blockReason").asText();
        if (!blocked.isBlank() && !"BLOCK_REASON_UNSPECIFIED".equals(blocked)) throw blocked();
        JsonNode candidates=response.path("candidates");
        if (!candidates.isArray() || candidates.size()!=1) throw invalid();
        JsonNode candidate=candidates.get(0);
        String finish=candidate.path("finishReason").asText();
        if ("MAX_TOKENS".equals(finish))
            throw error("AI_OUTPUT_TRUNCATED","Model output exceeded its limit; use a smaller schema pair");
        if (Set.of("SAFETY","RECITATION","BLOCKLIST","PROHIBITED_CONTENT","SPII","MODEL_ARMOR").contains(finish)) throw blocked();
        if (!"STOP".equals(finish) || !candidate.path("content").path("parts").isArray()) throw invalid();
        StringBuilder content=new StringBuilder();
        for (JsonNode part:candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue;
            if (!part.path("text").isTextual() || part.has("functionCall") || part.has("inlineData")) throw invalid();
            content.append(part.path("text").textValue());
        }
        try {
            return decode(input,protocol,json.readTree(content.toString()));
        } catch (java.io.IOException | IllegalArgumentException ex) { throw invalid(); }
    }

    private Suggestions decode(Input input,MappingSuggestionProtocol protocol,JsonNode compact) {
        return new MappingSuggestionValidator().validate(input,protocol.expand(compact,json));
    }

    private HttpRequest.Builder request(String suffix,int seconds) {
        return HttpRequest.newBuilder(URI.create(API+properties.getModel()+suffix))
                .timeout(Duration.ofSeconds(seconds)).header("Accept","application/json")
                .header("x-goog-api-key",properties.getGeminiApiKey());
    }

    private PayloadValidationException httpError(int status) {
        return switch(status) {
            case 401,403 -> error("AI_AUTHENTICATION_FAILED","Gemini API credentials or access permissions were rejected");
            case 404 -> error("AI_MODEL_NOT_AVAILABLE","The configured Gemini model is not available");
            case 429 -> error("AI_RATE_LIMITED","Gemini quota or rate limit reached; manual mapping is available");
            case 400,422 -> error("AI_CONFIGURATION_INVALID","Gemini rejected the AI request configuration");
            case 408,504 -> error("AI_TIMEOUT","AI request timed out; manual mapping is available");
            default -> error("AI_UNAVAILABLE","Gemini could not process the request");
        };
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException ex) { throw invalid(); }
    }
    private PayloadValidationException invalid() { return error("AI_INVALID_OUTPUT","Model returned an invalid mapping result"); }
    private PayloadValidationException blocked() { return error("AI_CONTENT_BLOCKED","Gemini could not generate suggestions for this content"); }
}
