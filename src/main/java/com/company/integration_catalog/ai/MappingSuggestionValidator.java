package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.payload.MappingField;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.company.integration_catalog.ai.AiMappingDtos.*;

final class MappingSuggestionValidator {
    Suggestions validate(Input input,JsonNode output) {
        if (output==null || !output.isObject()) throw invalidOutput();
        for (String key:List.of("matches","unmatchedSourceFields","unmatchedTargetFields","warnings"))
            if (!output.path(key).isArray()) throw invalidOutput();
        Map<String,MappingField> source=index(input.sourceFields()), target=index(input.targetFields());
        Set<String> protectedTargets=new HashSet<>(),usedSources=new HashSet<>(),usedTargets=new HashSet<>();
        input.protectedMappings().forEach(mapping -> {
            protectedTargets.add(mapping.targetFieldPath());
            if (input.sourceSchemaId().equals(mapping.sourceSchemaId())) usedSources.add(mapping.sourceFieldPath());
        });
        List<Suggestion> suggestions=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        Set<String> seenTargets=new HashSet<>();
        for (JsonNode match:output.path("matches")) {
            String a=text(match,"sourcePath",500),b=text(match,"targetPath",500);
            String reason=text(match,"reason",240),band=text(match,"confidence",10);
            if (!source.containsKey(a) || !target.containsKey(b) || !seenTargets.add(b)) throw invalidOutput();
            Confidence confidence;
            try { confidence=Confidence.valueOf(band); } catch (IllegalArgumentException ex) { throw invalidOutput(); }
            if (protectedTargets.contains(b)) {
                addWarning(warnings,"A suggestion conflicting with an existing or protected connection was removed."); continue;
            }
            if (!compatible(source.get(a),target.get(b))) {
                addWarning(warnings,"A suggestion requiring type or array conversion was removed."); continue;
            }
            suggestions.add(new Suggestion(UUID.randomUUID().toString(),input.sourceSchemaId(),a,
                    input.targetSchemaId(),b,confidence,reason));
            usedSources.add(a); usedTargets.add(b);
        }
        usedTargets.addAll(protectedTargets);
        List<UnmatchedField> unmatchedSource=unmatched(output.path("unmatchedSourceFields"),source,usedSources);
        List<UnmatchedField> unmatchedTarget=unmatched(output.path("unmatchedTargetFields"),target,usedTargets);
        if (output.path("warnings").size()>5) throw invalidOutput();
        for (JsonNode warning:output.path("warnings")) {
            if (!warning.isTextual() || warning.textValue().length()>240) throw invalidOutput();
            addWarning(warnings,warning.textValue());
        }
        return new Suggestions(List.copyOf(suggestions),unmatchedSource,unmatchedTarget,List.copyOf(warnings));
    }

    private List<UnmatchedField> unmatched(JsonNode entries,Map<String,MappingField> fields,Set<String> used) {
        Map<String,UnmatchedField> explained=new HashMap<>();
        Set<String> codes=Set.of("AMBIGUOUS","NO_COMPATIBLE_FIELD","TYPE_MISMATCH","INSUFFICIENT_CONTEXT","TRANSFORMATION_REQUIRED");
        for (JsonNode item:entries) {
            String path=text(item,"fieldPath",500),code=text(item,"code",40),reason=text(item,"reason",240);
            if (!fields.containsKey(path) || !codes.contains(code) || explained.containsKey(path)) throw invalidOutput();
            explained.put(path,new UnmatchedField(path,code,reason));
        }
        return fields.keySet().stream().filter(path -> !used.contains(path)).sorted()
                .map(path -> explained.getOrDefault(path,new UnmatchedField(path,"NO_CONFIDENT_MATCH",
                        "No match could be identified with sufficient confidence."))).toList();
    }

    private boolean compatible(MappingField source,MappingField target) {
        if (source.array()!=target.array()) return false;
        if (source.type().equals("unknown") || target.type().equals("unknown")) return true;
        return source.type().equals(target.type()) || source.type().equals("integer") && target.type().equals("number");
    }
    private Map<String,MappingField> index(List<MappingField> fields) {
        Map<String,MappingField> indexed=new TreeMap<>(); fields.forEach(field -> indexed.put(field.path(),field)); return indexed;
    }
    private void addWarning(List<String> warnings,String value) { if (!warnings.contains(value) && warnings.size()<10) warnings.add(value); }
    private String text(JsonNode object,String key,int limit) {
        JsonNode value=object.path(key);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length()>limit) throw invalidOutput();
        return value.textValue();
    }
    private static PayloadValidationException invalidOutput() {
        return new PayloadValidationException(503,"AI_INVALID_OUTPUT","Model returned an invalid mapping result");
    }
}
