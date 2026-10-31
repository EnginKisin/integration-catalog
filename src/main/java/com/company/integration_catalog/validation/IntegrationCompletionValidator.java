package com.company.integration_catalog.validation;

import com.company.integration_catalog.exception.IntegrationCompletionException;
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
        if (integration.isReviewRequired()) {
            errors.add("Review the preserved environment settings, schemas and mappings, then confirm the review before completing");
        }
        if (blank(integration.getName()) || integration.getDescription() == null
                || integration.getSourceSystem() == null || integration.getDestinationSystem() == null
                || integration.getSourcePayloadFormat() == null || integration.getDestinationPayloadFormat() == null
                || integration.getHasMiddleware() == null || integration.getIsSync() == null) {
            errors.add("Basic integration information is incomplete");
        }
        boolean middleware = Boolean.TRUE.equals(integration.getHasMiddleware());
        if (middleware != (integration.getMiddlewareSystem() != null)) {
            errors.add("Middleware selection is inconsistent");
        }
        validateConfigs(integration.getEnvironmentConfigs(), middleware, errors);
        Set<SchemaType> required = requiredSchemas(middleware, Boolean.TRUE.equals(integration.getIsSync()));
        Set<SchemaType> allowed = requiredSchemas(middleware, true);
        Map<Long, MappingSchema> schemasById = new HashMap<>();
        Set<SchemaType> present = EnumSet.noneOf(SchemaType.class);
        for (MappingSchema schema : integration.getMappingSchemas()) {
            if (schema.getId() != null) {
                schemasById.put(schema.getId(), schema);
            }
            if (schema.getSchemaType() == null || !allowed.contains(schema.getSchemaType())) {
                errors.add("Schema type is incompatible with the integration flow: " + schema.getSchemaType());
                continue;
            }
            if (!present.add(schema.getSchemaType())) {
                errors.add("Duplicate schema type: " + schema.getSchemaType());
            }
            try {
                PayloadSchemas.validateFlow(integration, schema.getSchemaType(), PayloadSchemas.format(schema));
                PayloadSchemas.validateStored(schema);
                PayloadSchemas.validateTarget(integration, schema);
            } catch (PayloadValidationException ex) {
                errors.add(ex.getMessage() + ": " + schema.getSchemaType());
            }
        }
        for (SchemaType type : required) {
            if (!present.contains(type)) {
                errors.add("Required schema is missing: " + type);
            }
        }
        for (FieldMapping mapping : integration.getFieldMappings()) {
            MappingSchema source = mapping.getSourceSchema() == null ? null
                    : schemasById.get(mapping.getSourceSchema().getId());
            MappingSchema target = mapping.getTargetSchema() == null ? null
                    : schemasById.get(mapping.getTargetSchema().getId());
            if (source == null || target == null || blank(mapping.getSourceFieldPath())
                    || blank(mapping.getTargetFieldPath()) || !validMappingPair(source, target, middleware)) {
                errors.add("Field mapping has an invalid schema connection or empty field path: " + mapping.getId());
            } else {
                try {
                    PayloadSchemas.validateMappingPath(source, mapping.getSourceFieldPath());
                    PayloadSchemas.validateMappingPath(target, mapping.getTargetFieldPath(),
                            !middleware && Objects.equals(source.getId(), target.getId()));
                } catch (PayloadValidationException ex) {
                    errors.add(ex.getMessage() + ": " + mapping.getId());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IntegrationCompletionException(errors);
        }
    }

    private void validateConfigs(List<EnvironmentConfig> configs, boolean middleware, List<String> errors) {
        if (configs.isEmpty()) {
            errors.add("At least one environment configuration is required");
        }
        Map<EnvironmentType, Set<ConfigTarget>> environments = new EnumMap<>(EnvironmentType.class);
        for (EnvironmentConfig config : configs) {
            if (config.getEnvironmentType() == null || config.getConfigTarget() == null) {
                errors.add("Environment type and configuration target are required");
                continue;
            }
            String label = config.getEnvironmentType() + "/" + config.getConfigTarget();
            Set<ConfigTarget> targets = environments.computeIfAbsent(config.getEnvironmentType(),
                    ignored -> EnumSet.noneOf(ConfigTarget.class));
            if (!targets.add(config.getConfigTarget())) {
                errors.add("Duplicate environment configuration: " + label);
            }
            if (!middleware && config.getConfigTarget() == ConfigTarget.MIDDLEWARE) {
                errors.add("Middleware configuration is incompatible with the integration flow: " + label);
            }
            if (blank(config.getUrl()) || config.getAuthType() == null) {
                errors.add("Environment URL and authentication type are required: " + label);
            }
            if ((config.getTimeoutMs() != null && config.getTimeoutMs() <= 0)
                    || (config.getRetryCount() != null && config.getRetryCount() < 0)
                    || (config.getRetryIntervalMs() != null && config.getRetryIntervalMs() < 0)) {
                errors.add("Invalid timeout or retry settings: " + label);
            }
        }
        environments.forEach((environment, targets) -> {
            if (!targets.contains(ConfigTarget.DESTINATION)) {
                errors.add("Destination configuration is missing for environment: " + environment);
            }
            if (middleware && !targets.contains(ConfigTarget.MIDDLEWARE)) {
                errors.add("Middleware configuration is missing for environment: " + environment);
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
