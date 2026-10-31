package com.company.integration_catalog.payload;

import com.company.integration_catalog.dto.mapping_schema.CsvOptionsDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.model.enums.SchemaType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

public final class PayloadSchemas {
    public static final int MAX_TEXT_BYTES = 1024 * 1024;
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PayloadSchemas() {}

    public static PayloadFormat format(MappingSchema schema) {
        return schema.getFormat() == null ? PayloadFormat.JSON : schema.getFormat();
    }

    public static boolean hasIndependentTarget(MappingSchema schema) {
        return schema.getTargetFormat() != null || schema.getTargetSchemaDefinition() != null
                || schema.getTargetSamplePayload() != null || schema.getTargetRawContent() != null;
    }

    public static PayloadFormat sideFormat(MappingSchema schema, boolean targetSide) {
        if (!targetSide || !hasIndependentTarget(schema)) return format(schema);
        return schema.getTargetFormat() == null ? PayloadFormat.JSON : schema.getTargetFormat();
    }

    public static PayloadFormat expectedTargetFormat(Integration integration, SchemaType type) {
        return switch (type) {
            case SOURCE_TO_DEST_REQUEST -> integration.getDestinationPayloadFormat();
            case DEST_TO_SOURCE_RESPONSE -> integration.getSourcePayloadFormat();
            default -> throw invalid("INVALID_SCHEMA_TYPE", "Middleware flows use a separate target schema");
        };
    }

    public static PayloadFormat expectedFormat(Integration integration, SchemaType type) {
        if (type == null) throw invalid("INVALID_SCHEMA_TYPE", "Schema type is required");
        return switch (type) {
            case SOURCE_TO_MW_REQUEST, MW_TO_SOURCE_RESPONSE, SOURCE_TO_DEST_REQUEST -> integration.getSourcePayloadFormat();
            case MW_TO_DEST_REQUEST, DEST_TO_MW_RESPONSE, DEST_TO_SOURCE_RESPONSE -> integration.getDestinationPayloadFormat();
        };
    }

    public static void validateFlow(Integration integration, SchemaType type, PayloadFormat format) {
        boolean middleware = Boolean.TRUE.equals(integration.getHasMiddleware());
        if (type == null || !type.isCompatibleWithFlow(middleware)) {
            throw invalid("INVALID_SCHEMA_TYPE", "Schema type is incompatible with the integration flow");
        }
        PayloadFormat expected = expectedFormat(integration, type);
        if (expected != null && expected != format) {
            throw invalid("FORMAT_MISMATCH", "Expected " + expected + " for " + type);
        }
    }

    public static MappingSchema prepare(Integration integration, MappingSchemaRequestDTO request, MultipartFile upload) {
        PayloadFormat format = request.getFormat();
        if (format == null) {
            if (upload != null) throw invalid("FORMAT_REQUIRED", "Format is required for file uploads");
            format = PayloadFormat.JSON;
        }
        validateFlow(integration, request.getSchemaType(), format);
        if (request.getSamplePayload() != null && format != PayloadFormat.JSON)
            throw invalid("INVALID_CONTENT", "samplePayload is only supported for a JSON source");
        if (request.getCsvOptions() != null && format != PayloadFormat.CSV) {
            throw invalid("INVALID_CONTENT", "CSV options are only supported for CSV");
        }
        MappingSchema result = MappingSchema.builder().integration(integration).schemaType(request.getSchemaType())
                .format(format).schemaDefinition(Map.of()).build();
        byte[] bytes = null;
        String text = request.getRawContent();
        if (upload != null) {
            if (text != null || request.getSchemaDefinition() != null) {
                throw invalid("INVALID_CONTENT", "Upload metadata must not also contain content");
            }
            checkSize(upload.getSize(), format == PayloadFormat.EXCEL ? MAX_FILE_BYTES : MAX_TEXT_BYTES);
            String name = safeFileName(upload.getOriginalFilename());
            validateExtension(format, name);
            try (InputStream input = upload.getInputStream()) {
                bytes = input.readNBytes((format == PayloadFormat.EXCEL ? MAX_FILE_BYTES : MAX_TEXT_BYTES) + 1);
            } catch (IOException ex) {
                throw invalid("INVALID_FILE", "Could not read uploaded file");
            }
            checkSize(bytes.length, format == PayloadFormat.EXCEL ? MAX_FILE_BYTES : MAX_TEXT_BYTES);
            result.setFileName(name);
            if (format != PayloadFormat.EXCEL) text = decodeUtf8(bytes);
        }
        if (format != PayloadFormat.JSON && request.getSchemaDefinition() != null) {
            throw invalid("INVALID_CONTENT", "Schema definition is generated by the backend for this format");
        }
        switch (format) {
            case JSON -> {
                if (upload == null && text != null) throw invalid("INVALID_CONTENT", "Use schemaDefinition for JSON");
                Map<String, Object> definition = request.getSchemaDefinition();
                if (definition == null && upload == null) definition = request.getSamplePayload();
                if (upload != null) {
                    try {
                        definition = JSON.readValue(stripBom(text), new TypeReference<Map<String, Object>>() {});
                    } catch (IOException ex) {
                        throw invalid("INVALID_JSON", "JSON content must be an object");
                    }
                    result.setRawContent(text);
                }
                if (definition == null) throw invalid("INVALID_JSON", "Schema definition must be a JSON object");
                try {
                    checkSize(JSON.writeValueAsBytes(definition).length, MAX_TEXT_BYTES);
                } catch (IOException ex) {
                    throw invalid("INVALID_JSON", "Could not read JSON definition");
                }
                result.setSchemaDefinition(definition);
                result.setSamplePayload(request.getSamplePayload());
                for (Map<String, Object> extra : java.util.Arrays.asList(request.getSamplePayload())) {
                    if (extra != null) {
                        try { checkSize(JSON.writeValueAsBytes(extra).length, MAX_TEXT_BYTES); }
                        catch (IOException ex) { throw invalid("INVALID_JSON", "Could not read JSON example"); }
                    }
                }
                if (request.getSamplePayload() != null) JsonMappingFields.fields(result, false, 1000);
                result.setContentType("application/json");
            }
            case XML, IDOC -> {
                requireText(text);
                result.setRawContent(text);
                result.setSchemaDefinition(xmlDefinition(text, format == PayloadFormat.IDOC));
                result.setContentType("application/xml");
            }
            case CSV -> {
                requireText(text);
                CsvOptionsDTO options = request.getCsvOptions();
                String delimiter = options == null || options.getDelimiter() == null ? "," : options.getDelimiter();
                boolean header = options == null || options.getHasHeader() == null || options.getHasHeader();
                validateCsv(text, delimiter, header);
                result.setCsvDelimiter(delimiter);
                result.setCsvHasHeader(header);
                result.setRawContent(text);
                result.setContentType("text/csv");
                if (bytes == null) {
                    bytes = text.getBytes(StandardCharsets.UTF_8);
                    result.setFileName("sample.csv");
                }
            }
            case EXCEL -> {
                if (bytes == null) throw invalid("FILE_REQUIRED", "Excel requires an .xls or .xlsx file upload");
                validateExcel(bytes, result.getFileName());
                result.setContentType(result.getFileName().toLowerCase(Locale.ROOT).endsWith(".xlsx")
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/vnd.ms-excel");
            }
        }
        prepareTarget(integration, request, result);
        result.setSampleFile(bytes);
        return result;
    }

    private static void prepareTarget(Integration integration, MappingSchemaRequestDTO request, MappingSchema result) {
        boolean supplied = request.getTargetFormat() != null || request.getTargetRawContent() != null
                || request.getTargetSchemaDefinition() != null || request.getTargetSamplePayload() != null;
        if (!supplied) return;
        if (Boolean.TRUE.equals(integration.getHasMiddleware()))
            throw invalid("INVALID_CONTENT", "Middleware flows use a separate target schema");
        PayloadFormat expected = expectedTargetFormat(integration, request.getSchemaType());
        PayloadFormat target = request.getTargetFormat();
        if (target == null) target = request.getTargetRawContent() != null ? expected : PayloadFormat.JSON;
        if (target == null || target != expected)
            throw invalid("FORMAT_MISMATCH", "Expected " + expected + " for the independent target");
        if (!target.supportsFieldMapping())
            throw invalid("MAPPING_NOT_SUPPORTED", "Independent targets support JSON, XML and IDOC");
        result.setTargetFormat(target);
        if (target == PayloadFormat.JSON) {
            if (request.getTargetRawContent() != null)
                throw invalid("INVALID_CONTENT", "Use targetSchemaDefinition or targetSamplePayload for JSON");
            result.setTargetSchemaDefinition(request.getTargetSchemaDefinition());
            result.setTargetSamplePayload(request.getTargetSamplePayload());
            for (var extra : java.util.Arrays.asList(request.getTargetSchemaDefinition(), request.getTargetSamplePayload())) {
                if (extra != null) {
                    try { checkSize(JSON.writeValueAsBytes(extra).length, MAX_TEXT_BYTES); }
                    catch (IOException ex) { throw invalid("INVALID_JSON", "Could not read target JSON"); }
                }
            }
            JsonMappingFields.fields(result, true, 1000);
        } else {
            if (request.getTargetSchemaDefinition() != null || request.getTargetSamplePayload() != null)
                throw invalid("INVALID_CONTENT", "XML/IDOC target definitions are generated from targetRawContent");
            requireText(request.getTargetRawContent());
            result.setTargetRawContent(request.getTargetRawContent());
            result.setTargetSchemaDefinition(xmlDefinition(request.getTargetRawContent(), target == PayloadFormat.IDOC));
        }
    }

    public static void validateTarget(Integration integration, MappingSchema schema) {
        if (Boolean.TRUE.equals(integration.getHasMiddleware())) {
            if (hasIndependentTarget(schema)) throw invalid("INVALID_CONTENT", "Middleware flow cannot contain an independent target");
            return;
        }
        PayloadFormat expected = expectedTargetFormat(integration, schema.getSchemaType());
        if (!hasIndependentTarget(schema)) {
            if (expected != format(schema)) throw invalid("SAMPLE_REQUIRED", "Mixed-format direct flows require an independent target");
            return;
        }
        if (sideFormat(schema, true) != expected)
            throw invalid("FORMAT_MISMATCH", "Independent target format does not match the integration flow");
        if (expected == PayloadFormat.JSON) JsonMappingFields.fields(schema, true, 1000);
        else if (expected == PayloadFormat.XML || expected == PayloadFormat.IDOC) {
            requireText(schema.getTargetRawContent());
            if (!xmlDefinition(schema.getTargetRawContent(), expected == PayloadFormat.IDOC).equals(schema.getTargetSchemaDefinition()))
                throw invalid("INVALID_SCHEMA", "Parsed target XML fields do not match content");
        } else throw invalid("MAPPING_NOT_SUPPORTED", "Independent target format does not support field mapping");
    }

    public static void validateStored(MappingSchema schema) {
        PayloadFormat format = format(schema);
        switch (format) {
            case JSON -> {
                if (schema.getSchemaDefinition() == null || schema.getSchemaDefinition().isEmpty()) {
                    throw invalid("EMPTY_SCHEMA", "Schema definition is empty");
                }
            }
            case XML, IDOC -> {
                requireText(schema.getRawContent());
                if (!xmlDefinition(schema.getRawContent(), format == PayloadFormat.IDOC).equals(schema.getSchemaDefinition())) {
                    throw invalid("INVALID_SCHEMA", "Parsed XML fields do not match content");
                }
            }
            case CSV -> {
                requireSample(schema);
                requireText(schema.getRawContent());
                validateCsv(schema.getRawContent(), schema.getCsvDelimiter(), Boolean.TRUE.equals(schema.getCsvHasHeader()));
            }
            case EXCEL -> {
                requireSample(schema);
                validateExcel(schema.getSampleFile(), schema.getFileName());
            }
        }
    }

    public static void validateMappingPath(MappingSchema schema, String path) {
        validateMappingPath(schema, path, false);
    }

    public static void validateMappingPath(MappingSchema schema, String path, boolean targetSide) {
        boolean independent = targetSide && hasIndependentTarget(schema);
        PayloadFormat effective = sideFormat(schema, independent);
        if (!effective.supportsFieldMapping()) {
            throw invalid("MAPPING_NOT_SUPPORTED", "CSV and Excel samples do not support field mapping");
        }
        if (effective == PayloadFormat.XML || effective == PayloadFormat.IDOC) {
            Map<String, Object> definition = independent ? schema.getTargetSchemaDefinition() : schema.getSchemaDefinition();
            if (definition == null || !containsPath(definition.get("fields"), path)) {
                throw invalid("FIELD_PATH_NOT_FOUND", "Field path does not exist in the content: " + path);
            }
        }
        if (effective == PayloadFormat.JSON && (independent
                ? schema.getTargetSamplePayload() != null || schema.getTargetSchemaDefinition() != null
                : schema.getSamplePayload() != null)) {
            if (JsonMappingFields.fields(schema, independent, 1000).stream()
                    .noneMatch(field -> field.path().equals(JsonMappingFields.canonical(path)))) {
                throw invalid("FIELD_PATH_NOT_FOUND", "Field path does not exist in the JSON side: " + path);
            }
        }
    }

    private static boolean containsPath(Object fields, String path) {
        if (!(fields instanceof List<?> list)) return false;
        for (Object field : list) {
            if (field instanceof Map<?, ?> node && (Objects.equals(node.get("path"), path)
                    || containsPath(node.get("children"), path))) return true;
        }
        return false;
    }

    public static Map<String, Object> xmlDefinition(String text, boolean idoc) {
        return parseXml(text, idoc, false);
    }

    public static Map<String, Object> xmlMappingDefinition(String text, boolean idoc) {
        requireText(text);
        return parseXml(text, idoc, true);
    }

    private static Map<String, Object> parseXml(String text, boolean idoc, boolean withValues) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseURI, namespace) -> {
            throw new XMLStreamException("External resources are disabled");
        });
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        boolean foundIdoc = false;
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new StringReader(stripBom(text)));
            String encoding = reader.getCharacterEncodingScheme();
            if (encoding != null && !encoding.equalsIgnoreCase("UTF-8")) {
                throw invalid("INVALID_ENCODING", "Only UTF-8 XML is supported");
            }
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw invalid("INVALID_XML", "DTD and entity references are not supported");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (stack.size() >= 64 || nodes.size() >= 10000) {
                        throw invalid("XML_COMPLEXITY_LIMIT", "XML exceeds 64 levels or 10000 fields");
                    }
                    String name = qualified(reader.getNamespaceURI(), reader.getLocalName());
                    String path = (stack.isEmpty() ? "" : stack.peek().path) + "/" + name;
                    if (path.length() > 500) throw invalid("XML_COMPLEXITY_LIMIT", "XML field path exceeds 500 characters");
                    boolean repeated = !stack.isEmpty() && !stack.peek().seen.add(path);
                    List<Map<String, Object>> siblings = stack.isEmpty() ? roots : children(nodes.get(stack.peek().path));
                    if (!nodes.containsKey(path)) {
                        Map<String, Object> node = node(reader.getLocalName(), path, "element");
                        node.put("namespaceUri", Objects.toString(reader.getNamespaceURI(), ""));
                        nodes.put(path, node);
                        siblings.add(node);
                    }
                    if (repeated) nodes.get(path).put("repeated", true);
                    if (!stack.isEmpty()) nodes.get(stack.peek().path).put("type", "object");
                    foundIdoc |= reader.getLocalName().equals("IDOC");
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String attrPath = path + "/@" + qualified(reader.getAttributeNamespace(i), reader.getAttributeLocalName(i));
                        if (attrPath.length() > 500 || nodes.size() >= 10000) {
                            throw invalid("XML_COMPLEXITY_LIMIT", "XML field limit exceeded");
                        }
                        if (!nodes.containsKey(attrPath)) {
                            Map<String, Object> attr = node(reader.getAttributeLocalName(i), attrPath, "attribute");
                            attr.put("namespaceUri", Objects.toString(reader.getAttributeNamespace(i), ""));
                            nodes.put(attrPath, attr);
                            children(nodes.get(path)).add(attr);
                        }
                        if (withValues) sampleValue(nodes.get(attrPath), reader.getAttributeValue(i));
                    }
                    stack.push(new Frame(path));
                } else if (withValues && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && !stack.isEmpty() && stack.peek().text.length() < 256) {
                    String value = reader.getText();
                    stack.peek().text.append(value, 0, Math.min(value.length(), 256 - stack.peek().text.length()));
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    Frame frame = stack.pop();
                    if (withValues) sampleValue(nodes.get(frame.path), frame.text.toString());
                }
            }
            if (roots.isEmpty()) throw invalid("INVALID_XML", "An XML root element is required");
            if (idoc && !foundIdoc) throw invalid("INVALID_IDOC", "IDOC XML must contain an IDOC element");
            return Map.of("kind", idoc ? "idoc-xml" : "xml", "fields", roots);
        } catch (XMLStreamException ex) {
            String location = ex.getLocation() == null ? "" : " at line " + ex.getLocation().getLineNumber()
                    + ", column " + ex.getLocation().getColumnNumber();
            throw invalid("INVALID_XML", "Malformed or unsupported XML" + location);
        } finally {
            if (reader != null) try { reader.close(); } catch (XMLStreamException ignored) { }
        }
    }

    @SuppressWarnings("unchecked")
    private static void sampleValue(Map<String, Object> node, String value) {
        value = value.trim();
        if (value.isEmpty()) return;
        value = value.substring(0, Math.min(value.length(), 128));
        List<String> samples = (List<String>) node.computeIfAbsent("sampleValues", key -> new ArrayList<String>());
        if (samples.size() < 3 && !samples.contains(value)) samples.add(value);
    }

    private static Map<String, Object> node(String name, String path, String kind) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name);
        node.put("path", path);
        node.put("kind", kind);
        node.put("type", "string");
        node.put("repeated", false);
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }

    private static String qualified(String namespace, String local) {
        return namespace == null || namespace.isEmpty() ? local : "{" + namespace + "}" + local;
    }

    private static class Frame {
        final String path;
        final Set<String> seen = new HashSet<>();
        final StringBuilder text = new StringBuilder();
        Frame(String path) { this.path = path; }
    }

    private static void validateCsv(String text, String delimiter, boolean header) {
        if (delimiter == null || delimiter.length() != 1 || "\r\n\"\0".indexOf(delimiter.charAt(0)) >= 0) {
            throw invalid("INVALID_CSV_OPTIONS", "CSV delimiter must be one character other than quote, newline or NUL");
        }
        try (var parser = CSVFormat.RFC4180.builder().setDelimiter(delimiter.charAt(0))
                .setIgnoreEmptyLines(true).get().parse(new StringReader(stripBom(text)))) {
            int columns = -1;
            long rows = 0;
            for (var record : parser) {
                if (columns == -1) {
                    columns = record.size();
                    if (header) {
                        Set<String> names = new HashSet<>();
                        for (String name : record) {
                            if (name.isBlank() || !names.add(name)) throw invalid("INVALID_CSV", "CSV headers must be nonblank and unique");
                        }
                    }
                }
                if (record.size() != columns) throw invalid("INVALID_CSV", "Inconsistent column count at record " + record.getRecordNumber());
                rows++;
            }
            if (rows == 0) throw invalid("INVALID_CSV", "CSV content is empty");
        } catch (IOException | UncheckedIOException | IllegalArgumentException ex) {
            throw invalid("INVALID_CSV", "Malformed CSV content");
        }
    }

    private static void validateExcel(byte[] bytes, String name) {
        checkSize(bytes.length, MAX_FILE_BYTES);
        validateExtension(PayloadFormat.EXCEL, name);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            boolean xls = workbook instanceof HSSFWorkbook;
            if (xls != name.toLowerCase(Locale.ROOT).endsWith(".xls") || workbook.getNumberOfSheets() == 0) {
                throw invalid("INVALID_EXCEL", "Workbook format must match its extension and contain a worksheet");
            }
        } catch (PayloadValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("INVALID_EXCEL", "A readable, unencrypted .xls or .xlsx workbook is required");
        }
    }

    private static void requireSample(MappingSchema schema) {
        if (schema.getSampleFile() == null || schema.getSampleFile().length == 0 || schema.getFileName() == null) {
            throw invalid("FILE_REQUIRED", "A sample file is required");
        }
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank()) throw invalid("EMPTY_CONTENT", "Content must not be empty");
        checkSize(text.getBytes(StandardCharsets.UTF_8).length, MAX_TEXT_BYTES);
    }

    private static void checkSize(long size, int maximum) {
        if (size <= 0) throw invalid("EMPTY_CONTENT", "Content must not be empty");
        if (size > maximum) throw new PayloadValidationException(413, "PAYLOAD_TOO_LARGE", "Content exceeds " + maximum + " bytes", Map.of("maxBytes", maximum));
    }

    private static String safeFileName(String name) {
        if (name == null) throw invalid("INVALID_FILE_NAME", "File name is required");
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        if (base.isBlank() || base.length() > 255 || base.chars().anyMatch(Character::isISOControl)) {
            throw invalid("INVALID_FILE_NAME", "Invalid file name");
        }
        return base;
    }

    private static void validateExtension(PayloadFormat format, String name) {
        if (name == null) throw invalid("INVALID_FILE_NAME", "File name is required");
        String lower = name.toLowerCase(Locale.ROOT);
        boolean valid = switch (format) {
            case JSON -> lower.endsWith(".json");
            case XML -> lower.endsWith(".xml");
            case IDOC -> lower.endsWith(".xml") || lower.endsWith(".idoc");
            case CSV -> lower.endsWith(".csv");
            case EXCEL -> lower.endsWith(".xls") || lower.endsWith(".xlsx");
        };
        if (!valid) throw invalid("UNSUPPORTED_FILE_EXTENSION", "File extension is incompatible with " + format);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw invalid("INVALID_ENCODING", "Only UTF-8 text files are supported");
        }
    }

    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static PayloadValidationException invalid(String code, String message) {
        return new PayloadValidationException(code, message);
    }
}
