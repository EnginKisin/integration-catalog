package com.company.integration_catalog.validation;

import com.company.integration_catalog.exception.IntegrationCompletionException;
import com.company.integration_catalog.exception.Violation;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import org.springframework.stereotype.Component;

import java.util.*;
import com.company.integration_catalog.payload.PayloadSchemas;
import com.company.integration_catalog.exception.PayloadValidationException;

@Component
public class IntegrationCompletionValidator {

    public void validate(Integration integration) {
        List<String> errors = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        if (integration.isReviewRequired()) {
            add(errors, violations, "REVIEW_REQUIRED", Map.of(), "Review the preserved environment settings, schemas and mappings, then confirm the review before completing");
        }
        if (blank(integration.getName()) || integration.getDescription() == null
                || integration.getSourceSystem() == null || integration.getDestinationSystem() == null
                || integration.getSourcePayloadFormat() == null || integration.getDestinationPayloadFormat() == null
                || integration.getHasMiddleware() == null || integration.getIsSync() == null) {
            add(errors, violations, "BASIC_INFORMATION_REQUIRED", Map.of(), "Basic integration information is incomplete");
        }
        boolean middleware = Boolean.TRUE.equals(integration.getHasMiddleware());
        if (middleware != (integration.getMiddlewareSystem() != null)) {
            add(errors, violations, "INVALID_MIDDLEWARE_SELECTION", Map.of(), "Middleware selection is inconsistent");
        }
        validateConfigs(integration.getEnvironmentConfigs(), middleware, errors, violations);
        Set<SchemaType> required = requiredSchemas(middleware, Boolean.TRUE.equals(integration.getIsSync()));
        Set<SchemaType> allowed = requiredSchemas(middleware, true);
        Map<Long, MappingSchema> schemasById = new HashMap<>();
        Set<SchemaType> present = EnumSet.noneOf(SchemaType.class);
        for (MappingSchema schema : integration.getMappingSchemas()) {
            if (schema.getId() != null) {
                schemasById.put(schema.getId(), schema);
            }
            if (schema.getSchemaType() == null || !allowed.contains(schema.getSchemaType())) {
                add(errors, violations, "INVALID_SCHEMA_TYPE", metadata("schemaType", schema.getSchemaType()), "Schema type is incompatible with the integration flow: " + schema.getSchemaType());
                continue;
            }
            if (!present.add(schema.getSchemaType())) {
                add(errors, violations, "DUPLICATE_SCHEMA_TYPE", metadata("schemaType", schema.getSchemaType()), "Duplicate schema type: " + schema.getSchemaType());
            }
            try {
                PayloadSchemas.validateFlow(integration, schema.getSchemaType(), PayloadSchemas.format(schema));
                PayloadSchemas.validateStored(schema);
                PayloadSchemas.validateTarget(integration, schema);
            } catch (PayloadValidationException ex) {
                add(errors, violations, ex.getCode(), metadata("schemaType", schema.getSchemaType()), ex.getMessage() + ": " + schema.getSchemaType());
            }
        }
        for (SchemaType type : required) {
            if (!present.contains(type)) {
                add(errors, violations, "REQUIRED_SCHEMA_MISSING", Map.of("schemaType", type), "Required schema is missing: " + type);
            }
        }
        for (FieldMapping mapping : integration.getFieldMappings()) {
            MappingSchema source = mapping.getSourceSchema() == null ? null
                    : schemasById.get(mapping.getSourceSchema().getId());
            MappingSchema target = mapping.getTargetSchema() == null ? null
                    : schemasById.get(mapping.getTargetSchema().getId());
            if (source == null || target == null || blank(mapping.getSourceFieldPath())
                    || blank(mapping.getTargetFieldPath()) || !validMappingPair(source, target, middleware)) {
                add(errors, violations, "INVALID_MAPPING_CONNECTION", metadata("mappingId", mapping.getId()), "Field mapping has an invalid schema connection or empty field path: " + mapping.getId());
            } else {
                try {
                    PayloadSchemas.validateMappingPath(source, mapping.getSourceFieldPath());
                    PayloadSchemas.validateMappingPath(target, mapping.getTargetFieldPath(),
                            !middleware && Objects.equals(source.getId(), target.getId()));
                } catch (PayloadValidationException ex) {
                    add(errors, violations, ex.getCode(), metadata("mappingId", mapping.getId()), ex.getMessage() + ": " + mapping.getId());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IntegrationCompletionException(errors, violations);
        }
    }

    private void add(List<String> errors, List<Violation> violations, String code,
            Map<String, Object> params, String detail) {
        errors.add(detail);
        violations.add(new Violation(null, code, params));
    }

    private Map<String, Object> metadata(String key, Object value) {
        return value == null ? Map.of() : Map.of(key, value);
    }

    private void validateConfigs(List<EnvironmentConfig> configs, boolean middleware, List<String> errors, List<Violation> violations) {
        if (configs.isEmpty()) {
            add(errors, violations, "ENVIRONMENT_CONFIG_REQUIRED", Map.of(), "At least one environment configuration is required");
        }
        Map<EnvironmentType, Set<ConfigTarget>> environments = new EnumMap<>(EnvironmentType.class);
        for (EnvironmentConfig config : configs) {
            if (config.getEnvironmentType() == null || config.getConfigTarget() == null) {
                add(errors, violations, "INVALID_ENVIRONMENT_CONFIG", Map.of(), "Environment type and configuration target are required");
                continue;
            }
            String label = config.getEnvironmentType() + "/" + config.getConfigTarget();
            Set<ConfigTarget> targets = environments.computeIfAbsent(config.getEnvironmentType(),
                    ignored -> EnumSet.noneOf(ConfigTarget.class));
            if (!targets.add(config.getConfigTarget())) {
                add(errors, violations, "DUPLICATE_ENVIRONMENT_CONFIG", Map.of("environment", config.getEnvironmentType(), "target", config.getConfigTarget()), "Duplicate environment configuration: " + label);
            }
            if (!middleware && config.getConfigTarget() == ConfigTarget.MIDDLEWARE) {
                add(errors, violations, "MIDDLEWARE_CONFIG_NOT_ALLOWED", Map.of("environment", config.getEnvironmentType()), "Middleware configuration is incompatible with the integration flow: " + label);
            }
            if (blank(config.getUrl()) || config.getAuthType() == null) {
                add(errors, violations, "INVALID_ENVIRONMENT_CONFIG", Map.of("environment", config.getEnvironmentType(), "target", config.getConfigTarget()), "Environment URL and authentication type are required: " + label);
            }
            if ((config.getTimeoutMs() != null && config.getTimeoutMs() <= 0)
                    || (config.getRetryCount() != null && config.getRetryCount() < 0)
                    || (config.getRetryIntervalMs() != null && config.getRetryIntervalMs() < 0)) {
                add(errors, violations, "INVALID_ENVIRONMENT_CONFIG", Map.of("environment", config.getEnvironmentType(), "target", config.getConfigTarget()), "Invalid timeout or retry settings: " + label);
            }
        }
        environments.forEach((environment, targets) -> {
            if (!targets.contains(ConfigTarget.DESTINATION)) {
                add(errors, violations, "DESTINATION_CONFIG_REQUIRED", Map.of("environment", environment), "Destination configuration is missing for environment: " + environment);
            }
            if (middleware && !targets.contains(ConfigTarget.MIDDLEWARE)) {
                add(errors, violations, "MIDDLEWARE_CONFIG_REQUIRED", Map.of("environment", environment), "Middleware configuration is missing for environment: " + environment);
            }
        });
    }

    private Set<SchemaType> requiredSchemas(boolean middleware, boolean sync) {
        Set<SchemaType> required = middleware
                ? EnumSet.of(SchemaType.SOURCE_TO_MW_REQUEST, SchemaType.MW_TO_DEST_REQUEST)
                : EnumSet.of(SchemaType.SOURCE_TO_DEST_REQUEST);
        if (sync) {
            required.addAll(middleware
                    ? EnumSet.of(SchemaType.DEST_TO_MW_RESPONSE, SchemaType.MW_TO_SOURCE_RESPONSE)
                    : EnumSet.of(SchemaType.DEST_TO_SOURCE_RESPONSE));
        }
        return required;
    }

    private boolean validMappingPair(MappingSchema source, MappingSchema target, boolean middleware) {
        if (!middleware) {
            return source.getSchemaType() != null && source.getSchemaType() == target.getSchemaType();
        }
        return (source.getSchemaType() == SchemaType.SOURCE_TO_MW_REQUEST
                && target.getSchemaType() == SchemaType.MW_TO_DEST_REQUEST)
                || (source.getSchemaType() == SchemaType.DEST_TO_MW_RESPONSE
                && target.getSchemaType() == SchemaType.MW_TO_SOURCE_RESPONSE);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
