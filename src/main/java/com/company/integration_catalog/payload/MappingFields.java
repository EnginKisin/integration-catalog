package com.company.integration_catalog.payload;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.model.enums.PayloadFormat;
import java.util.*;

public final class MappingFields {
    private MappingFields() {}

    public static List<MappingField> fields(MappingSchema schema, boolean targetSide, int maxFields) {
        if (targetSide && !PayloadSchemas.hasIndependentTarget(schema))
            throw new PayloadValidationException(422, "SAMPLE_REQUIRED", "An independent target definition or sample is required");
        PayloadFormat format = PayloadSchemas.sideFormat(schema, targetSide);
        if (format == PayloadFormat.JSON) return JsonMappingFields.fields(schema, targetSide, maxFields);
        if (format != PayloadFormat.XML && format != PayloadFormat.IDOC)
            throw new PayloadValidationException(422, "AI_FORMAT_NOT_SUPPORTED", "Mapping suggestions support JSON, XML and IDOC");
        String raw = targetSide ? schema.getTargetRawContent() : schema.getRawContent();
        if (raw == null || raw.isBlank())
            throw new PayloadValidationException(422, "SAMPLE_REQUIRED", "XML/IDOC content is required for each side");
        Map<String,Object> tree = PayloadSchemas.xmlMappingDefinition(raw, format == PayloadFormat.IDOC);
        List<MappingField> result = new ArrayList<>();
        flatten(tree.get("fields"), false, result, maxFields);
        if (result.isEmpty()) throw new PayloadValidationException(422, "SAMPLE_REQUIRED", "XML has no mappable fields");
        return List.copyOf(result);
    }

    public static String canonical(MappingSchema schema, boolean targetSide, String path) {
        return PayloadSchemas.sideFormat(schema, targetSide) == PayloadFormat.JSON ? JsonMappingFields.canonical(path) : path;
    }

    private static void flatten(Object entries, boolean repeatedParent, List<MappingField> fields, int limit) {
        if (!(entries instanceof List<?> list)) return;
        for (Object entry : list) {
            if (!(entry instanceof Map<?,?> node)) continue;
            boolean repeated = repeatedParent || Boolean.TRUE.equals(node.get("repeated"));
            Object children = node.get("children");
            boolean hasElements = children instanceof List<?> nodes && nodes.stream().anyMatch(child ->
                    child instanceof Map<?,?> map && "element".equals(map.get("kind")));
            if ("attribute".equals(node.get("kind")) || !hasElements) {
                if (fields.size() >= limit)
                    throw new PayloadValidationException(422, "TOO_MANY_FIELDS", "XML field limit exceeded", java.util.Map.of("maxFields", limit));
                List<Object> values = node.get("sampleValues") instanceof List<?> samples ? new ArrayList<>(samples) : List.of();
                fields.add(new MappingField(node.get("path").toString(), "unknown", values.isEmpty(), repeated, List.copyOf(values)));
            }
            flatten(children, repeated, fields, limit);
        }
    }
}
