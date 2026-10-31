package com.company.integration_catalog.payload;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.entity.MappingSchema;
import java.util.*;

public final class JsonMappingFields {
    private JsonMappingFields() {}

    public static List<MappingField> fields(MappingSchema schema, boolean targetSide, int maxFields) {
        Map<String, Object> definition = targetSide ? schema.getTargetSchemaDefinition() : schema.getSchemaDefinition();
        Map<String, Object> sample = targetSide ? schema.getTargetSamplePayload() : schema.getSamplePayload();
        if (sample == null && (definition == null || definition.isEmpty())) {
            throw error("SAMPLE_REQUIRED", "An independent JSON definition or sample is required for each side");
        }
        List<MappingField> result = new ArrayList<>();
        if (definition != null && definition.get("properties") instanceof Map<?, ?> properties) {
            schemaFields(properties, "", sample, false, 0, result, maxFields);
        } else if (sample != null) {
            sampleFields(sample, "", false, 0, result, maxFields);
        } else if (definition != null && !definition.containsKey("type") && !definition.containsKey("$schema")) {
            sampleFields(definition, "", false, 0, result, maxFields);
        } else {
            throw error("SAMPLE_REQUIRED", "JSON properties or a samplePayload object are required");
        }
        if (result.isEmpty()) throw error("SAMPLE_REQUIRED", "JSON input has no mappable fields");
        return List.copyOf(result);
    }

    public static String canonical(String path) {
        if (path == null || path.startsWith("/")) return path;
        return "/" + path.replaceAll("\\[\\d*]", ".*").replace('.', '/');
    }

    private static void schemaFields(Map<?, ?> properties, String prefix, Object sample, boolean array,
            int depth, List<MappingField> result, int limit) {
        depth(depth);
        for (var entry : properties.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> field)) {
                throw error("INVALID_JSON_SCHEMA", "Each property must have an object definition");
            }
            String path = prefix + "/" + escape(key);
            Object value = sample instanceof Map<?, ?> map ? map.get(key) : null;
            if (field.get("properties") instanceof Map<?, ?> children) {
                schemaFields(children, path, value, array, depth + 1, result, limit);
            } else if ("array".equals(field.get("type")) && field.get("items") instanceof Map<?, ?> items) {
                Object first = value instanceof List<?> list && !list.isEmpty() ? list.getFirst() : null;
                if (items.get("properties") instanceof Map<?, ?> children) {
                    schemaFields(children, path + "/*", first, true, depth + 1, result, limit);
                } else {
                    add(result, new MappingField(path + "/*", type(items.get("type")), nullable(items.get("type")), true,
                            values(value)), limit);
                }
            } else {
                add(result, new MappingField(path, type(field.get("type")), nullable(field.get("type")), array, values(value)), limit);
            }
        }
    }

    private static void sampleFields(Object value, String path, boolean array, int depth, List<MappingField> result, int limit) {
        depth(depth);
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            for (var entry : map.entrySet()) {
                sampleFields(entry.getValue(), path + "/" + escape(entry.getKey().toString()), array, depth + 1, result, limit);
            }
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) add(result, new MappingField(path + "/*", "unknown", true, true, List.of()), limit);
            else {

                LinkedHashMap<String, MappingField> merged = new LinkedHashMap<>();
                for (Object item : list.stream().limit(3).toList()) {
                    List<MappingField> child = new ArrayList<>();
                    sampleFields(item, path + "/*", true, depth + 1, child, limit);
                    for (MappingField field : child) merged.merge(field.path(), field, JsonMappingFields::merge);
                }
                for (MappingField field : merged.values()) add(result, field, limit);
            }
        } else {
            String type = value == null ? "unknown" : value instanceof Boolean ? "boolean"
                    : value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                        || value instanceof java.math.BigInteger ? "integer"
                    : value instanceof Number ? "number" : value instanceof Map ? "object" : "string";
            add(result, new MappingField(path, type, value == null, array, values(value)), limit);
        }
    }

    private static MappingField merge(MappingField a, MappingField b) {
        List<Object> values = new ArrayList<>(a.sampleValues());
        b.sampleValues().stream().filter(value -> !values.contains(value)).limit(3 - values.size()).forEach(values::add);
        return new MappingField(a.path(), a.type().equals(b.type()) ? a.type() : "unknown",
                a.nullable() || b.nullable(), a.array(), List.copyOf(values));
    }

    private static List<Object> values(Object value) {
        if (value instanceof List<?> list) return list.stream().filter(Objects::nonNull)
                .filter(item -> !(item instanceof Map || item instanceof List)).limit(3).map(JsonMappingFields::shortValue).toList();
        return value == null || value instanceof Map ? List.of() : List.of(shortValue(value));
    }

    private static Object shortValue(Object value) {
        if (value instanceof String text) return text.substring(0, Math.min(text.length(), 128));
        return value;
    }

    private static void add(List<MappingField> fields, MappingField field, int limit) {
        if (field.path().length() > 500) throw error("INPUT_TOO_COMPLEX", "JSON field path exceeds 500 characters");
        if (fields.size() >= limit) throw new PayloadValidationException(422, "TOO_MANY_FIELDS", "JSON field limit exceeded", Map.of("maxFields", limit));
        fields.add(field);
    }
    private static String type(Object type) {
        if (type instanceof List<?> list) return list.stream().filter(item -> !"null".equals(item))
                .findFirst().map(Object::toString).orElse("unknown");
        return type instanceof String text ? text : "unknown";
    }
    private static boolean nullable(Object type) { return type instanceof List<?> list && list.contains("null"); }
    private static String escape(String key) { return key.replace("~", "~0").replace("/", "~1"); }
    private static void depth(int depth) { if (depth > 32) throw error("INPUT_TOO_COMPLEX", "JSON nesting exceeds 32 levels"); }
    private static PayloadValidationException error(String code, String message) { return new PayloadValidationException(422, code, message); }
}
