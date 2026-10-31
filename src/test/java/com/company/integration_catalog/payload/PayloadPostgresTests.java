package com.company.integration_catalog.payload;

import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import com.company.integration_catalog.repository.*;
import com.company.integration_catalog.service.MappingService;
import jakarta.persistence.EntityManager;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "catalog.payload.postgres-test", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${catalog.payload.test-db-url:jdbc:postgresql://localhost:5432/integration_payload_test_20261025}",
        "spring.datasource.username=catalog_payload_test", "spring.datasource.password=payload-test-only-password",
        "catalog.credentials.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "catalog.bootstrap.username=payload-test-admin", "catalog.bootstrap.password=payload-test-admin-password",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"
})
@Transactional
class PayloadPostgresTests {
    @Autowired EntityManager em;
    @Autowired SystemRepository systems;
    @Autowired IntegrationRepository integrations;
    @Autowired MappingService mappings;

    @ParameterizedTest @EnumSource(PayloadFormat.class)
    void persistsAndReadsContentAndBinarySamplesAcrossDatabaseReload(PayloadFormat format) throws Exception {
        CatalogSystem source = systems.save(CatalogSystem.builder().name("Payload source").description("").build());
        CatalogSystem destination = systems.save(CatalogSystem.builder().name("Payload destination").description("").build());
        Integration integration = integrations.save(Integration.builder().name("Payload roundtrip").description("")
                .sourceSystem(source).destinationSystem(destination).hasMiddleware(false).isSync(false)
                .sourcePayloadFormat(format).destinationPayloadFormat(format).status(IntegrationStatus.DRAFT).build());
        Long integrationId = integration.getId();
        var request = MappingSchemaRequestDTO.builder().schemaType(SchemaType.SOURCE_TO_DEST_REQUEST).format(format).build();
        switch (format) {
            case JSON -> request.setSchemaDefinition(Map.of("type", "object"));
            case XML -> request.setRawContent("<order>\r\n <id>42</id>\r\n</order>");
            case IDOC -> request.setRawContent("<ORDERS05><IDOC><E1EDK01><BELNR>42</BELNR></E1EDK01></IDOC></ORDERS05>");
            case CSV -> request.setRawContent("id,name\r\n42,Örnek\r\n");
            case EXCEL -> { }
        }
        Long schemaId;
        byte[] excel = null;
        if (format == PayloadFormat.EXCEL) {
            try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
                workbook.createSheet("Orders").createRow(0).createCell(0).setCellValue("id");
                workbook.write(output);
                excel = output.toByteArray();
            }
            schemaId = mappings.uploadSchema(integrationId, request,
                    new MockMultipartFile("file", "orders.xlsx", "application/octet-stream", excel)).getId();
        } else {
            schemaId = mappings.saveSchema(integrationId, request).getId();
        }
        em.flush();
        em.clear();
        var response = mappings.getSchemasByIntegration(integrationId).getFirst();
        assertThat(response.getFormat()).isEqualTo(format);
        assertThat(response.getRawContent()).isEqualTo(request.getRawContent());
        assertThat(response.isMappingSupported()).isEqualTo(format.supportsFieldMapping());
        if (format == PayloadFormat.EXCEL) {
            assertThat(mappings.getSampleFile(integrationId, schemaId).bytes()).isEqualTo(excel);
        } else {
            assertThat(mappings.saveSchema(integrationId, request).getId()).isEqualTo(schemaId);
        }
        if (format == PayloadFormat.CSV) {
            assertThat(mappings.getSampleFile(integrationId, schemaId).bytes())
                    .isEqualTo(request.getRawContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
