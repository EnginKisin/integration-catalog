package com.company.integration_catalog.ai;

import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.exception.ErrorCode;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.payload.MappingField;
import com.company.integration_catalog.payload.MappingFields;
import com.company.integration_catalog.payload.PayloadSchemas;
import com.company.integration_catalog.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.company.integration_catalog.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import static com.company.integration_catalog.ai.AiMappingDtos.*;

@Service
public class AiMappingService {
    private final IntegrationRepository integrations;
    private final FieldMappingRepository fields;
    private final AiJobRepository jobs;
    private final MappingAppendReceiptRepository receipts;
    private final IntegrationMapper mapper;
    private final ObjectMapper json;
    private final AiProperties properties;
    private final List<MappingSuggestionProvider> providers;
    private final Clock clock;

    public AiMappingService(IntegrationRepository integrations, FieldMappingRepository fields, AiJobRepository jobs,
            MappingAppendReceiptRepository receipts, IntegrationMapper mapper, ObjectMapper json,
            AiProperties properties, List<MappingSuggestionProvider> providers, Clock clock) {
        this.integrations=integrations; this.fields=fields; this.jobs=jobs; this.receipts=receipts; this.mapper=mapper;
        this.json=json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.properties=properties; this.providers=providers; this.clock=clock;
    }

    public Capabilities capabilities() {
        String code = !properties.isEnabled() ? "AI_DISABLED" : providers.stream()
                .filter(provider -> provider.name().equals(properties.getProvider())).findFirst()
                .map(MappingSuggestionProvider::availabilityCode).orElse("AI_PROVIDER_NOT_SUPPORTED");
        return new Capabilities(properties.isEnabled(), "READY".equals(code), properties.getProvider(),
                properties.getModel(), List.of("JSON", "XML", "IDOC"), properties.getMaxFields(), code);
    }

    @Transactional
    public Context context(Long id) {
        Integration integration = locked(id);
        return new Context(revision(integration), integration.getMappingSchemas().stream()
                .sorted(Comparator.comparing(MappingSchema::getId)).map(mapper::toSchemaResponse).toList(),
                integration.getFieldMappings().stream().sorted(Comparator.comparing(FieldMapping::getId))
                        .map(mapper::toMappingResponse).toList());
    }

    @Transactional
    public JobView create(Long id, String user, String key, CreateJob request) {
        validateKey(key);
        Integration integration = locked(id);
        String hash = hash(request);
        var replay = jobs.findByIntegrationIdAndCreatedByAndIdempotencyKey(id, user, key);
        if (replay.isPresent()) {
            sameRequest(hash, replay.get().getRequestHash());
            return view(replay.get());
        }
        provider();
        requireRevision(integration, request.expectedContextRevision());
        MappingSchema source = schema(integration, request.sourceSchemaId());
        MappingSchema target = schema(integration, request.targetSchemaId());
        requirePair(integration, source, target);
        List<MappingField> sourceFields = MappingFields.fields(source, false, properties.getMaxFields());
        List<MappingField> targetFields = MappingFields.fields(target, sameSchema(source, target), properties.getMaxFields());
        List<Connection> protectedMappings = new ArrayList<>();
        integration.getFieldMappings().stream().filter(mapping -> mapping.getTargetSchema().getId().equals(target.getId()))
                .forEach(mapping -> protectedMappings.add(new Connection(mapping.getSourceSchema().getId(),
                        MappingFields.canonical(mapping.getSourceSchema(), false, mapping.getSourceFieldPath()), target.getId(),
                        MappingFields.canonical(target, sameSchema(mapping.getSourceSchema(), target), mapping.getTargetFieldPath()))));
        for (Connection connection : request.protectedMappings()) {
            if (!source.getId().equals(connection.sourceSchemaId()) || !target.getId().equals(connection.targetSchemaId()))
                throw error(422,"INVALID_SCHEMA_PAIR","Protected mappings must belong to the selected schema pair");
            requirePath(sourceFields, MappingFields.canonical(source, false, connection.sourceFieldPath()));
            requirePath(targetFields, MappingFields.canonical(target, sameSchema(source, target), connection.targetFieldPath()));
            protectedMappings.add(new Connection(source.getId(), MappingFields.canonical(source, false, connection.sourceFieldPath()),
                    target.getId(), MappingFields.canonical(target, sameSchema(source, target), connection.targetFieldPath())));
        }
        boolean useValues = request.options() == null || request.options().useSampleValues();
        Input input = new Input(source.getId(), target.getId(), request.expectedContextRevision(),
                useValues ? sourceFields : withoutValues(sourceFields), useValues ? targetFields : withoutValues(targetFields),
                List.copyOf(protectedMappings), PayloadSchemas.sideFormat(source, false),
                PayloadSchemas.sideFormat(target, sameSchema(source, target)));
        int inputBytes=encode(input).getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > properties.getMaxInputBytes())
            throw error(413,"AI_INPUT_TOO_LARGE","Normalized mapping input exceeds the configured byte limit");

        if ((inputBytes+1)/2 + 1536 + properties.getMaxOutputTokens() > properties.getContextTokens())
            throw error(413,"AI_CONTEXT_LIMIT","Input exceeds the context budget; reduce samples/fields or increase AI_CONTEXT_TOKENS");
        if (jobs.countByStatusIn(List.of("QUEUED","RUNNING")) >= properties.getMaxPendingJobs())
            throw error(429,"AI_QUEUE_FULL","AI queue is full; retry later");
        AiJob job = new AiJob();
        job.setId(UUID.randomUUID().toString()); job.setIntegration(integration); job.setCreatedBy(user);
        job.setIdempotencyKey(key); job.setRequestHash(hash); job.setContextRevision(request.expectedContextRevision());
        job.setStatus("QUEUED"); job.setProvider(properties.getProvider()); job.setModel(properties.getModel());
        job.setCreatedAt(clock.instant()); job.setExpiresAt(clock.instant().plusSeconds(properties.getJobTtlHours()*3600L));
        job.setInputSnapshot(asMap(input));
        return view(jobs.save(job));
    }

    @Transactional(readOnly=true)
    public JobView status(Long id, String jobId) { return view(job(id,jobId)); }

    @Transactional(readOnly=true)
    public Result result(Long id, String jobId) {
        AiJob job = job(id,jobId);
        requireResult(job);
        Suggestions result = json.convertValue(job.getResult(), Suggestions.class);
        return new Result(jobId, job.getContextRevision(), !revision(job.getIntegration()).equals(job.getContextRevision()),
                job.getProvider(), job.getModel(), result.suggestions(), result.unmatchedSourceFields(),
                result.unmatchedTargetFields(), result.warnings());
    }

    @Transactional
    public AppendResponse append(Long id, String user, String key, AppendRequest request) {
        validateKey(key);
        Integration integration = locked(id);
        String hash = hash(request);
        var replay = receipts.findByIntegrationIdAndCreatedByAndIdempotencyKey(id,user,key);
        if (replay.isPresent()) {
            sameRequest(hash,replay.get().getRequestHash());
            return json.convertValue(replay.get().getResponse(),AppendResponse.class);
        }
        requireRevision(integration,request.expectedContextRevision());
        Map<String,Suggestion> proposals = new HashMap<>();
        AiJob job = null;
        if (request.aiJobId()!=null) {
            job=job(id,request.aiJobId()); requireResult(job);
            if (!job.getContextRevision().equals(request.expectedContextRevision()))
                throw error(409,"CONTEXT_CHANGED","AI input changed; generate new suggestions");
            json.convertValue(job.getResult(),Suggestions.class).suggestions().forEach(item -> proposals.put(item.suggestionId(),item));
        }
        Set<String> occupied = new HashSet<>();
        integration.getFieldMappings().forEach(mapping -> occupied.add(mapping.getTargetSchema().getId()+":"
                +MappingFields.canonical(mapping.getTargetSchema(), sameSchema(mapping.getSourceSchema(), mapping.getTargetSchema()),
                        mapping.getTargetFieldPath())));
        Set<String> usedSuggestions = new HashSet<>();
        List<FieldMapping> additions = new ArrayList<>();
        for (AppendMapping item : request.mappings()) {
            MappingSchema source=schema(integration,item.sourceSchemaId()), target=schema(integration,item.targetSchemaId());
            requirePair(integration,source,target);
            String sourcePath=MappingFields.canonical(source,false,item.sourceFieldPath()),
                    targetPath=MappingFields.canonical(target,sameSchema(source,target),item.targetFieldPath());
            requirePath(MappingFields.fields(source,false,1000),sourcePath);
            requirePath(MappingFields.fields(target,sameSchema(source,target),1000),targetPath);
            if (!occupied.add(target.getId()+":"+targetPath))
                throw error(409,"MAPPING_CONFLICT","A target field already has a mapping: "+targetPath);
            String origin="MANUAL";
            Suggestion proposal=null;
            if (item.suggestionId()!=null) {
                proposal=proposals.get(item.suggestionId());
                if (proposal==null || !source.getId().equals(proposal.sourceSchemaId()) || !target.getId().equals(proposal.targetSchemaId()))
                    throw error(422,"INVALID_SUGGESTION","Suggestion does not belong to this job and schema pair");
                if (!usedSuggestions.add(item.suggestionId())) throw error(422,"INVALID_SUGGESTION","Suggestion used more than once");
                origin=sourcePath.equals(proposal.sourceFieldPath()) && targetPath.equals(proposal.targetFieldPath())
                        ? "AI_ACCEPTED" : "AI_EDITED";
            }
            additions.add(FieldMapping.builder().integration(integration).sourceSchema(source).targetSchema(target)
                    .sourceFieldPath(sourcePath).targetFieldPath(targetPath).origin(origin)
                    .isAiGenerated("AI_ACCEPTED".equals(origin)).aiConfidenceScore(null)
                    .aiJobId(proposal==null?null:job.getId()).aiSuggestionId(item.suggestionId()).build());
        }
        List<FieldMapping> saved=fields.saveAll(additions);
        integration.getFieldMappings().addAll(saved);
        integration.setStatus(IntegrationStatus.DRAFT);
        List<FieldMappingResponseDTO> responses=saved.stream().map(mapper::toMappingResponse).toList();
        AppendResponse response=new AppendResponse(revision(integration),responses);
        MappingAppendReceipt receipt=new MappingAppendReceipt();
        receipt.setId(UUID.randomUUID().toString()); receipt.setIntegration(integration); receipt.setCreatedBy(user);
        receipt.setIdempotencyKey(key); receipt.setRequestHash(hash); receipt.setCreatedAt(clock.instant());
        receipt.setResponse(asMap(response)); receipts.save(receipt);
        return response;
    }

    public MappingSuggestionProvider provider() {
        if (!properties.isEnabled()) throw error(503,"AI_DISABLED","AI suggestions are disabled; manual mapping is available");
        MappingSuggestionProvider selected=providers.stream().filter(provider -> provider.name().equals(properties.getProvider())).findFirst()
                .orElseThrow(() -> error(503,"AI_PROVIDER_NOT_SUPPORTED","The configured AI provider is not installed"));
        selected.validateConfiguration();
        return selected;
    }

    private Integration locked(Long id) { return integrations.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("INTEGRATION_NOT_FOUND", "Integration not found: "+id)); }
    private AiJob job(Long id,String jobId) { return jobs.findByIdAndIntegrationId(jobId,id)
            .orElseThrow(() -> new ResourceNotFoundException("AI_JOB_NOT_FOUND", "AI job not found in integration")); }
    private MappingSchema schema(Integration integration,Long id) { return integration.getMappingSchemas().stream()
            .filter(schema -> schema.getId().equals(id)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("SCHEMA_NOT_FOUND", "Schema not found in integration: "+id)); }
    private boolean sameSchema(MappingSchema source,MappingSchema target) { return source.getId().equals(target.getId()); }

    private void requirePair(Integration integration,MappingSchema source,MappingSchema target) {
        Set<PayloadFormat> supported=EnumSet.of(PayloadFormat.JSON,PayloadFormat.XML,PayloadFormat.IDOC);
        if (!supported.contains(integration.getSourcePayloadFormat()) || !supported.contains(integration.getDestinationPayloadFormat())
                || !supported.contains(PayloadSchemas.format(source))
                || !supported.contains(PayloadSchemas.sideFormat(target,sameSchema(source,target))))
            throw error(422,"AI_FORMAT_NOT_SUPPORTED","Mapping suggestions support JSON, XML and IDOC in either direction");
        SchemaType a=source.getSchemaType(),b=target.getSchemaType();
        boolean allowed=Boolean.TRUE.equals(integration.getHasMiddleware())
                ? (a==SchemaType.SOURCE_TO_MW_REQUEST && b==SchemaType.MW_TO_DEST_REQUEST)
                    || (a==SchemaType.DEST_TO_MW_RESPONSE && b==SchemaType.MW_TO_SOURCE_RESPONSE)
                : sameSchema(source,target) && (a==SchemaType.SOURCE_TO_DEST_REQUEST || a==SchemaType.DEST_TO_SOURCE_RESPONSE);
        if (!allowed) throw error(422,"INVALID_SCHEMA_PAIR","Schema pair is incompatible with the integration flow");
        try {
            PayloadSchemas.validateFlow(integration, source.getSchemaType(), PayloadSchemas.format(source));
            PayloadSchemas.validateFlow(integration, target.getSchemaType(), PayloadSchemas.format(target));
            PayloadSchemas.validateTarget(integration, source);
            if (!sameSchema(source,target)) PayloadSchemas.validateTarget(integration,target);
        } catch (PayloadValidationException ex) {
            throw new PayloadValidationException(422, ex.getCode(), ex.getMessage(), ex.getParams());
        }
    }

    private void requirePath(List<MappingField> fields,String path) {
        if (fields.stream().noneMatch(field -> field.path().equals(path)))
            throw error(422,"FIELD_PATH_NOT_FOUND","Field path does not exist in this side: "+path);
    }
    private List<MappingField> withoutValues(List<MappingField> fields) {
        return fields.stream().map(field -> new MappingField(field.path(),field.type(),field.nullable(),field.array(),List.of())).toList();
    }
    private void requireRevision(Integration integration,String expected) {
        if (!revision(integration).equals(expected)) throw error(409,"CONTEXT_CHANGED","Schemas or mappings changed; reload the mapping context");
    }
    private void requireResult(AiJob job) {
        if (job.getExpiresAt().compareTo(clock.instant())<=0 || "EXPIRED".equals(job.getStatus()))
            throw error(410,"AI_RESULT_EXPIRED","AI result expired; generate new suggestions");
        if (!"SUCCEEDED".equals(job.getStatus())) throw error(409,"AI_RESULT_NOT_READY","AI result is not available; inspect job status");
    }
    private JobView view(AiJob job) {
        String status=job.getExpiresAt().compareTo(clock.instant())<=0?"EXPIRED":job.getStatus();
        String errorCode = "EXPIRED".equals(status) ? "AI_RESULT_EXPIRED" : job.getErrorCode();
        return new JobView(job.getId(),status,job.getCreatedAt(),job.getExpiresAt(),errorCode,
                errorCode == null ? null : ErrorCode.message(errorCode),
                "SUCCEEDED".equals(status)?"/api/v1/integrations/"+job.getIntegration().getId()+"/mappings/ai/jobs/"+job.getId()+"/result":null,
                List.of("QUEUED","RUNNING").contains(status)?2000:null);
    }
    private String revision(Integration integration) {
        Map<String,Object> state=new TreeMap<>();
        state.put("flow",Arrays.asList(integration.getHasMiddleware(),integration.getIsSync(),integration.getSourcePayloadFormat(),
                integration.getDestinationPayloadFormat(),integration.getSourceSystem()==null?null:integration.getSourceSystem().getId(),
                integration.getDestinationSystem()==null?null:integration.getDestinationSystem().getId(),
                integration.getMiddlewareSystem()==null?null:integration.getMiddlewareSystem().getId()));
        state.put("schemas",integration.getMappingSchemas().stream().sorted(Comparator.comparing(MappingSchema::getId))
                .map(schema -> Arrays.asList(schema.getId(),schema.getSchemaType(),PayloadSchemas.format(schema),schema.getSchemaDefinition(),
                        schema.getRawContent(),schema.getSamplePayload(),schema.getTargetSchemaDefinition(),schema.getTargetSamplePayload(),
                        schema.getTargetFormat(),schema.getTargetRawContent())).toList());
        state.put("mappings",integration.getFieldMappings().stream().sorted(Comparator.comparing(FieldMapping::getId))
                .map(mapping -> Arrays.asList(mapping.getId(),mapping.getSourceSchema().getId(),mapping.getSourceFieldPath(),
                        mapping.getTargetSchema().getId(),mapping.getTargetFieldPath())).toList());
        return hash(state);
    }
    private String hash(Object object) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(object).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String encode(Object object) {
        try { return json.writeValueAsString(object); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Could not encode mapping context",ex); }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> asMap(Object object) { return json.convertValue(object,Map.class); }
    private void sameRequest(String hash,String existing) {
        if (!hash.equals(existing)) throw error(409,"IDEMPOTENCY_CONFLICT","Idempotency key was already used for a different request");
    }
    private void validateKey(String key) {
        if (key==null || !key.matches("[A-Za-z0-9._:-]{1,100}")) throw error(400,"INVALID_IDEMPOTENCY_KEY","A 1-100 character Idempotency-Key is required");
    }
    private static PayloadValidationException error(int status,String code,String message) { return new PayloadValidationException(status,code,message); }
}
