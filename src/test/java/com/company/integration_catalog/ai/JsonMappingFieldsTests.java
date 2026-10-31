package com.company.integration_catalog.ai;

import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.payload.JsonMappingFields;
import com.company.integration_catalog.payload.MappingField;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JsonMappingFieldsTests {
    @Test void extractsNestedSamplesWithoutConvertingIdentifiersToNumbers() {
        var schema=MappingSchema.builder().samplePayload(Map.of("customer",Map.of("id","000123"),
                "lines",List.of(Map.of("sku","A1","qty",2),Map.of("sku","B2","qty",3)))).build();
        var fields=JsonMappingFields.fields(schema,false,20);
        assertThat(fields).extracting(MappingField::path).containsExactlyInAnyOrder("/customer/id","/lines/*/sku","/lines/*/qty");
        assertThat(fields.stream().filter(field -> field.path().equals("/customer/id")).findFirst().orElseThrow())
                .satisfies(field -> { assertThat(field.type()).isEqualTo("string"); assertThat(field.sampleValues()).containsExactly("000123"); });
        assertThat(fields.stream().filter(field -> field.path().equals("/lines/*/sku")).findFirst().orElseThrow().sampleValues())
                .containsExactly("A1","B2");
    }
    @Test void extractsDeclaredTypesAndAnIndependentDirectTarget() {
        var schema=MappingSchema.builder().schemaDefinition(Map.of("type","object","properties",
                Map.of("id",Map.of("type","string"))))
                .targetSchemaDefinition(Map.of("type","object","properties",Map.of("customerId",Map.of("type",List.of("null","string")))))
                .build();
        assertThat(JsonMappingFields.fields(schema,false,10)).singleElement().satisfies(field -> assertThat(field.path()).isEqualTo("/id"));
        assertThat(JsonMappingFields.fields(schema,true,10)).singleElement().satisfies(field -> {
            assertThat(field.path()).isEqualTo("/customerId"); assertThat(field.nullable()).isTrue(); assertThat(field.type()).isEqualTo("string");
        });
    }
    @Test void doesNotPretendSourceDefinitionIsAnIndependentTarget() {
        var schema=MappingSchema.builder().schemaDefinition(Map.of("id","C1")).build();
        assertThatThrownBy(() -> JsonMappingFields.fields(schema,true,10)).isInstanceOfSatisfying(PayloadValidationException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("SAMPLE_REQUIRED"));
    }
    @Test void enforcesFieldAndDepthLimitsAndEscapesPointerKeys() {
        var schema=MappingSchema.builder().samplePayload(Map.of("a/b~c",1,"other",2)).build();
        assertThat(JsonMappingFields.fields(schema,false,2)).extracting(MappingField::path).contains("/a~1b~0c");
        assertThatThrownBy(() -> JsonMappingFields.fields(schema,false,1)).isInstanceOf(PayloadValidationException.class);
        Map<String,Object> nested=Map.of("leaf",1);
        for(int i=0;i<34;i++) nested=Map.of("child",nested);
        schema.setSamplePayload(nested);
        assertThatThrownBy(() -> JsonMappingFields.fields(schema,false,100)).isInstanceOf(PayloadValidationException.class);
    }
    @Test void supportsLegacyPathsAsAliasesForCanonicalPointerPaths() {
        assertThat(JsonMappingFields.canonical("orders[0].customerId")).isEqualTo("/orders/*/customerId");
        assertThat(JsonMappingFields.canonical("orders[].customerId")).isEqualTo("/orders/*/customerId");
        assertThat(JsonMappingFields.canonical("/a~1b")).isEqualTo("/a~1b");
    }
}
