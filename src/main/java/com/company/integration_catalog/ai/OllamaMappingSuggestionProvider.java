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

@Component
public class OllamaMappingSuggestionProvider implements MappingSuggestionProvider {
    private final AiProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public OllamaMappingSuggestionProvider(AiProperties properties,ObjectMapper json) {
        this(properties,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build());
    }

    OllamaMappingSuggestionProvider(AiProperties properties,ObjectMapper json,HttpClient client) {
        this.properties=properties;
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(262144).build());
        this.client=client;
    }

    @Override public String name() { return "ollama"; }

    @Override public String availabilityCode() {
        try {
            requireLocalModel();
            JsonNode response=exchange("/api/tags",null,3);
            if (!response.path("models").isArray()) return "AI_UNAVAILABLE";
            for (JsonNode model:response.path("models")) {
                if (properties.getModel().equals(model.path("name").asText())
                        || properties.getModel().equals(model.path("model").asText())) {
                    return model.hasNonNull("remote_host")?"AI_CLOUD_MODEL_FORBIDDEN":"READY";
                }
            }
            return "AI_MODEL_NOT_INSTALLED";
        } catch (PayloadValidationException ex) { return ex.getCode(); }
    }

    @Override public Suggestions suggest(Input input) {
        requireLocalModel();
        MappingSuggestionProtocol protocol=new MappingSuggestionProtocol(input);
        if (!protocol.hasCandidates()) {
            var empty=json.createObjectNode(); empty.putArray("matches"); empty.putArray("unmatched");
            return validate(input,protocol.expand(empty,json));
        }
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("model",properties.getModel()); body.put("stream",false); body.put("think",false);
        body.put("keep_alive","10m"); body.put("format",protocol.schema());
        body.put("options",Map.of("num_ctx",properties.getContextTokens(),"num_predict",properties.getMaxOutputTokens(),
                "temperature",0.2,"seed",42));
        body.put("messages",List.of(Map.of("role","system","content",MappingSuggestionProtocol.SYSTEM),
                Map.of("role","user","content",encode(protocol.evidence()))));
        long started=System.nanoTime();
        JsonNode response=exchange("/api/chat",body,properties.getRequestTimeoutSeconds());
        org.slf4j.LoggerFactory.getLogger(getClass()).info(
                "Ollama mapping completed elapsedMs={} inputFields={} outputTokens={}",
                (System.nanoTime()-started)/1_000_000,input.sourceFields().size()+input.targetFields().size(),
                response.path("eval_count").asInt(-1));
        if ("length".equals(response.path("done_reason").asText()))
            throw error("AI_OUTPUT_TRUNCATED","Model output exceeded its limit; use a smaller schema pair");
        if (!response.path("done").asBoolean(false) || !response.path("message").path("content").isTextual())
            throw invalidOutput();
        try {
            return validate(input,protocol.expand(json.readTree(response.path("message").path("content").textValue()),json));
        } catch (java.io.IOException | IllegalArgumentException ex) { throw invalidOutput(); }
    }

    Suggestions validate(Input input,JsonNode output) {
        return new MappingSuggestionValidator().validate(input,output);
    }

    private void requireLocalModel() {
        if (properties.getModel().toLowerCase(Locale.ROOT).contains(":cloud"))
            throw error("AI_CLOUD_MODEL_FORBIDDEN","Only downloaded local models are supported by this provider");
    }

    private JsonNode exchange(String path,Object body,int seconds) {
        try {
            URI base=URI.create(properties.getBaseUrl());
            if (!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null
                    || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
                    || base.getPath()!=null && !Set.of("","/").contains(base.getPath()))
                throw error("AI_CONFIGURATION_INVALID","AI_BASE_URL must be a server origin without credentials or a path");
            var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(seconds))
                    .header("Accept","application/json");
            if (body==null) builder.GET();
            else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(encode(body)));
            return new AiProviderHttpClient(json,client).exchange(builder.build(),status -> status==404
                    ? error("AI_MODEL_NOT_INSTALLED","The configured model is not installed in Ollama")
                    : error("AI_UNAVAILABLE","Ollama could not process the request"));
        } catch (IllegalArgumentException ex) {
            throw error("AI_CONFIGURATION_INVALID","Invalid AI server configuration");
        }
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (java.io.IOException ex) { throw invalidOutput(); }
    }
    private static PayloadValidationException error(String code,String message) { return new PayloadValidationException(503,code,message); }
    private static PayloadValidationException invalidOutput() { return error("AI_INVALID_OUTPUT","Model returned an invalid mapping result"); }

}
