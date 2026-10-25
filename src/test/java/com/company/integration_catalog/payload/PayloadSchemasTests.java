package com.company.integration_catalog.payload;

import com.company.integration_catalog.dto.mapping_schema.*;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.entity.*;
import com.company.integration_catalog.model.enums.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PayloadSchemasTests {
    private Integration integration(PayloadFormat format) {
        return Integration.builder().id(1L).hasMiddleware(false).sourcePayloadFormat(format)
                .destinationPayloadFormat(format).build();
    }

    private MappingSchemaRequestDTO request(PayloadFormat format, String content) {
        return MappingSchemaRequestDTO.builder().schemaType(SchemaType.SOURCE_TO_DEST_REQUEST)
                .format(format).rawContent(content).build();
    }

    @Test
    void keepsLegacyJsonContractAndAllowsEmptyDraft() {
        var request = request(null, null);
        request.setSchemaDefinition(Map.of());
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.JSON), request, null);
        assertThat(schema.getFormat()).isEqualTo(PayloadFormat.JSON);
        assertThatThrownBy(() -> PayloadSchemas.validateStored(schema)).isInstanceOf(PayloadValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{\"id\":1} {\"id\":2}"})
    void jsonUploadsRequireExactlyOneObject(String json) {
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.JSON), request(PayloadFormat.JSON, null),
                new MockMultipartFile("file", "sample.json", "application/json", json.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_JSON"));
    }

    @Test
    void xmlUploadPreservesOriginalFileBytesAndContent() {
        String xml = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<order>Örnek</order>\r\n";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.XML), request(PayloadFormat.XML, null),
                new MockMultipartFile("file", "sample.xml", "application/octet-stream", bytes));
        assertThat(schema.getRawContent()).isEqualTo(xml);
        assertThat(schema.getSampleFile()).isEqualTo(bytes);
        PayloadSchemas.validateStored(schema);
    }

    @Test
    void xmlPreservesWhitespaceAndProducesNamespacesAttributesAndRepeatedFields() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<o:order xmlns:o=\"urn:orders\" id=\"1\">\n  <o:item>A</o:item><o:item>B</o:item>\n</o:order>";
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.XML), request(PayloadFormat.XML, xml), null);
        assertThat(schema.getRawContent()).isEqualTo(xml);
        PayloadSchemas.validateMappingPath(schema, "/{urn:orders}order/@id");
        PayloadSchemas.validateMappingPath(schema, "/{urn:orders}order/{urn:orders}item");
        assertThat(schema.getSchemaDefinition().toString()).contains("repeated=true");
        PayloadSchemas.validateStored(schema);
        assertThatThrownBy(() -> PayloadSchemas.validateMappingPath(schema, "/order/missing"))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("FIELD_PATH_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<root>", "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><a>&x;</a>",
            "<!DOCTYPE a SYSTEM 'https://example.invalid/a.dtd'><a/>",
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a/>"})
    void rejectsMalformedXmlExternalResourcesAndNonUtf8Declarations(String xml) {
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.XML), request(PayloadFormat.XML, xml), null))
                .isInstanceOf(PayloadValidationException.class);
    }

    @Test
    void rejectsExcessivelyDeepXml() {
        String xml = "<a>".repeat(65) + "</a>".repeat(65);
        assertThatThrownBy(() -> PayloadSchemas.xmlDefinition(xml, false))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("XML_COMPLEXITY_LIMIT"));
    }

    @Test
    void idocAcceptsXmlEnvelopeAndRequiresIdocElement() {
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.IDOC),
                request(PayloadFormat.IDOC, "<ORDERS05><IDOC BEGIN=\"1\"><E1EDK01><BELNR>42</BELNR></E1EDK01></IDOC></ORDERS05>"), null);
        PayloadSchemas.validateMappingPath(schema, "/ORDERS05/IDOC/E1EDK01/BELNR");
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.IDOC), request(PayloadFormat.IDOC, "<order/>"), null))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_IDOC"));
    }

    @Test
    void pastedCsvKeepsExactBytesAndDoesNotGenerateFields() {
        String csv = "id;name\r\n1;\"Ali; Veli\"\r\n";
        var request = request(PayloadFormat.CSV, csv);
        request.setCsvOptions(new CsvOptionsDTO(";", true));
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.CSV), request, null);
        assertThat(schema.getSampleFile()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(schema.getSchemaDefinition()).isEmpty();
        assertThat(schema.getFileName()).isEqualTo("sample.csv");
        PayloadSchemas.validateStored(schema);
        assertThatThrownBy(() -> PayloadSchemas.validateMappingPath(schema, "id"))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("MAPPING_NOT_SUPPORTED"));
    }

    @Test
    void uploadedCsvPreservesBomAndSupportsHeaderlessTabSeparatedData() {
        byte[] bytes = "\uFEFF1\tAli\n2\tVeli".getBytes(StandardCharsets.UTF_8);
        var request = request(PayloadFormat.CSV, null);
        request.setCsvOptions(new CsvOptionsDTO("\t", false));
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.CSV), request,
                new MockMultipartFile("file", "../../örnek.csv", "text/plain", bytes));
        assertThat(schema.getFileName()).isEqualTo("örnek.csv");
        assertThat(schema.getSampleFile()).isEqualTo(bytes);
        PayloadSchemas.validateStored(schema);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id,id\n1,2", "id,\n1,2", "id,name\n1", "id,name\n1,\"broken"})
    void rejectsMalformedCsv(String csv) {
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.CSV), request(PayloadFormat.CSV, csv), null))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CSV"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void storesXlsAndXlsxAsExactBytesWithoutMapping(boolean xlsx) throws Exception {
        byte[] bytes;
        try (var workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("Orders").createRow(0).createCell(0).setCellValue("id");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        String name = xlsx ? "orders.xlsx" : "orders.xls";
        var schema = PayloadSchemas.prepare(integration(PayloadFormat.EXCEL), request(PayloadFormat.EXCEL, null),
                new MockMultipartFile("file", name, "application/octet-stream", bytes));
        assertThat(schema.getSampleFile()).isEqualTo(bytes);
        assertThat(schema.getRawContent()).isNull();
        assertThat(schema.getSchemaDefinition()).isEmpty();
        PayloadSchemas.validateStored(schema);
        assertThatThrownBy(() -> PayloadSchemas.validateMappingPath(schema, "A1")).isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.EXCEL), request(PayloadFormat.EXCEL, null),
                new MockMultipartFile("file", xlsx ? "wrong.xls" : "wrong.xlsx", "application/octet-stream", bytes)))
                .isInstanceOf(PayloadValidationException.class);
    }

    @Test
    void rejectsFakeExcelWrongExtensionsEmptyAndOversizedFilesAndInvalidEncoding() {
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.EXCEL), request(PayloadFormat.EXCEL, null),
                new MockMultipartFile("file", "fake.xlsx", "application/zip", "not a workbook".getBytes())))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_EXCEL"));
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.CSV), request(PayloadFormat.CSV, null),
                new MockMultipartFile("file", "a.exe", "text/csv", "id\n1".getBytes())))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_FILE_EXTENSION"));
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.CSV), request(PayloadFormat.CSV, null),
                new MockMultipartFile("file", "a.csv", "text/csv", new byte[0])))
                .isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.CSV), request(PayloadFormat.CSV, null),
                new MockMultipartFile("file", "a.csv", "text/csv", new byte[]{(byte) 0xff})))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_ENCODING"));
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration(PayloadFormat.CSV),
                request(PayloadFormat.CSV, "a".repeat(PayloadSchemas.MAX_TEXT_BYTES + 1)), null))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getStatus()).isEqualTo(413));
    }

    @Test
    void rejectsFormatAndTopologyMismatchAndAcceptsMixedMiddlewareFormats() {
        var integration = integration(PayloadFormat.CSV);
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration, request(PayloadFormat.XML, "<a/>"), null))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> assertThat(ex.getCode()).isEqualTo("FORMAT_MISMATCH"));
        integration.setHasMiddleware(true);
        integration.setDestinationPayloadFormat(PayloadFormat.XML);
        var csv = request(PayloadFormat.CSV, "id\n1");
        csv.setSchemaType(SchemaType.SOURCE_TO_MW_REQUEST);
        PayloadSchemas.prepare(integration, csv, null);
        var xml = request(PayloadFormat.XML, "<order/>");
        xml.setSchemaType(SchemaType.MW_TO_DEST_REQUEST);
        PayloadSchemas.prepare(integration, xml, null);
        xml.setSchemaType(SchemaType.SOURCE_TO_DEST_REQUEST);
        assertThatThrownBy(() -> PayloadSchemas.prepare(integration, xml, null)).isInstanceOf(PayloadValidationException.class);
    }
}
