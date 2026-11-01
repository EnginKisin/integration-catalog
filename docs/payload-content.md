# Payload content, sample files, and field mapping

This API stores catalog content and mapping definitions. It does not call external
systems, convert files, or execute saved mappings.

## Formats and flow model

| `format` | Content | Field mapping |
|---|---|---|
| `JSON` | `schemaDefinition` or `samplePayload` JSON object; both can be provided | Yes; AI/append validates paths; legacy manual source JSON without a sample has limited checks |
| `XML` | UTF-8 XML text and a backend-generated field tree | Yes; paths are checked against the tree |
| `IDOC` | XML containing an `IDOC` element and a generated field tree | Yes; paths are checked against the tree |
| `CSV` | UTF-8 text or a sample file, with delimiter/header options | No |
| `EXCEL` | `.xls` or `.xlsx` sample file | No |

Each integration/`schemaType` pair has one record. Saving again keeps its ID and
replaces source content/file metadata. An omitted independent target is kept if
its format still matches the current flow. Direct request samples use
`SOURCE_TO_DEST_REQUEST`. Middleware uses two samples: `SOURCE_TO_MW_REQUEST` and
`MW_TO_DEST_REQUEST`. Synchronous flows also require matching response records.
See the [full table](integration-workflow.md#environment-and-schema-rules).

Adding/removing middleware does not immediately delete current samples. The first
successful sample save for the new flow removes incompatible old schemas and
mappings that reference them as source or target. Adding middleware removes
`SOURCE_TO_DEST_REQUEST` and `DEST_TO_SOURCE_RESPONSE`. Removing middleware removes
all four middleware schema types. Current compatible samples and their mappings
are kept. For text/JSON saves and file uploads, cleanup and the new sample save
share one transaction. Failure keeps old samples/mappings. Later GETs return the
cleaned lists. The review flag still needs separate confirmation.

Cleanup runs on sample saves, not application restarts or GETs. If samples for
the new flow were saved before this cleanup feature existed, save a current,
compatible sample again with the same content. The update keeps its ID and
removes incompatible old schemas/mappings in the same transaction.

A content record's `format` must match these integration settings:

| `schemaType` | Expected format |
|---|---|
| `SOURCE_TO_MW_REQUEST`, `MW_TO_SOURCE_RESPONSE` | `sourcePayloadFormat` |
| `MW_TO_DEST_REQUEST`, `DEST_TO_MW_RESPONSE` | `destinationPayloadFormat` |
| `SOURCE_TO_DEST_REQUEST` | `sourcePayloadFormat` (sending source) |
| `DEST_TO_SOURCE_RESPONSE` | `destinationPayloadFormat` (responding target) |

A middleware flow can use CSV as source and XML as target, for example. **Both
ends** must support mapping. A CSV → XML pair stores both samples but cannot save
field mappings. Each direction of a direct flow remains one record. Its
independent target uses `targetFormat`, `targetSchemaDefinition`,
`targetSamplePayload`, and `targetRawContent`. Send JSON definition/sample objects
for JSON targets and raw text for XML/IDOC targets. The backend generates the XML
field tree. Target format matches `destinationPayloadFormat` for requests and
`sourcePayloadFormat` for responses. Mixed-format direct flows need an independent
target for completion and AI suggestions.
[Nine format pairs and sample bodies](ai-mapping.md#supported-format-pairs-and-sourcetarget-samples).

## Saving text or JSON objects

`POST /api/v1/integrations/10/mappings/schemas`, `Content-Type: application/json`.
Success returns 200 and the saved record. EDITOR/ADMIN and CSRF are required.

The examples below use direct flows. Integration `sourcePayloadFormat` must match
the submitted format. Middleware flows need the matching `schemaType` and the
source/target format listed above.

For backward compatibility, an omitted `format` means JSON.
If `schemaDefinition` is absent, `samplePayload` also becomes the definition.
Requests with neither are rejected. Provide `samplePayload` separately if AI
should use sample values. The full JSON Schema standard is not validated.

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "schemaDefinition": {"type": "object", "properties": {"id": {"type": "string"}}}
}
```

XML:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "XML",
  "rawContent": "<order>\r\n  <id>42</id>\r\n</order>"
}
```

IDOC; plain SAP records/text and binary formats are not accepted:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "IDOC",
  "rawContent": "<ORDERS05><IDOC BEGIN=\"1\"><E1EDK01><BELNR>42</BELNR></E1EDK01></IDOC></ORDERS05>"
}
```

CSV:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "CSV",
  "rawContent": "id;name\r\n42;Sample\r\n",
  "csvOptions": {"delimiter": ";", "hasHeader": true}
}
```

Without CSV options, `delimiter=","` and `hasHeader=true`. For a file without a
header, use `hasHeader=false`. For tabs, send `delimiter="\t"`. Pasted CSV also
creates a downloadable `sample.csv`. Do not send `schemaDefinition` for non-JSON
formats or `csvOptions` for non-CSV formats. For pasted JSON, the frontend parses
the text into an object and sends `schemaDefinition`.

## File upload and download

`POST /api/v1/integrations/10/mappings/schemas/upload`, `multipart/form-data`:

- `metadata`: an `application/json` part with `schemaType`, **required** `format`,
  and optional `csvOptions` for CSV. Do not repeat file content as `rawContent`
  or `schemaDefinition`. Direct flows may also send independent target format
  and content in this metadata.
- `file`: the file part.

Example metadata for Excel:

```json
{"schemaType":"SOURCE_TO_DEST_REQUEST","format":"EXCEL"}
```

Frontend example; use the existing session/CSRF workflow:

```javascript
const form = new FormData();
form.append("metadata", new Blob([JSON.stringify({
  schemaType: "SOURCE_TO_DEST_REQUEST",
  format: "EXCEL"
})], {type: "application/json"}));
form.append("file", file);
const response = await fetch(`/api/v1/integrations/${id}/mappings/schemas/upload`, {
  method: "POST",
  credentials: "include",
  headers: {[csrfHeaderName]: csrfToken},
  body: form
});
```

Do not set Content-Type manually; the browser supplies the multipart boundary.
This endpoint also accepts JSON/XML/IDOC/CSV files. JSON files must contain an
object, stored as `schemaDefinition`. The declared MIME type is not trusted;
both extension and actual content are checked.

| Format | Extension | Maximum content size |
|---|---|---|
| JSON | `.json` | 1 MiB |
| XML | `.xml` | 1 MiB |
| IDOC | `.xml`, `.idoc` with XML content | 1 MiB |
| CSV | `.csv` | 1 MiB |
| EXCEL | `.xls`, `.xlsx` | 5 MiB |

Text files must use UTF-8; a UTF-8 BOM is accepted. XML encoding declarations must
also be UTF-8. XML DTD/external resources/entities are disabled. Limits are 64
levels, 10000 fields, and 500 characters per field path. XML structure is checked,
but XSD, IDOC business rules, and SAP segment standards are not validated. Types
are not inferred with certainty from samples: leaves are shown as string and
nodes with children as object.

CSV supports quoted fields and line breaks. Column counts must match. If headers
are enabled, they must be nonempty and unique. A header-only file is accepted.
Excel must be a readable, unencrypted workbook with at least one worksheet.
There is no Excel field-tree/preview endpoint.

Original uploaded bytes are stored in PostgreSQL `bytea`. XML/IDOC raw text is
also stored in TEXT and the generated tree in `jsonb`. Text is not reformatted;
line endings are kept. File paths are discarded; only a cleaned file name is
stored. Sample content is not covered by credential encryption. Do not put real
credentials in samples.

`GET /api/v1/integrations/10/mappings/schemas/30/sample` returns the original file
as an attachment. VIEWER/EDITOR/ADMIN can download it. A schema outside the
integration, or a record without a file, returns 404. Pasted XML/IDOC uses
`rawContent` and has no download URL because no file was uploaded.

## Read responses and field paths

`GET /api/v1/integrations/10/mappings/schemas` returns an array. POST returns one
record with the same shape. Unrelated null fields are omitted from these examples
for readability.

JSON:

```json
{"id":30,"schemaType":"SOURCE_TO_DEST_REQUEST","format":"JSON","mappingSupported":true,"schemaDefinition":{"type":"object","properties":{"id":{"type":"string"}}}}
```

XML:

```json
{
  "id": 30,
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "XML",
  "mappingSupported": true,
  "rawContent": "<order>\r\n  <id>42</id>\r\n</order>",
  "schemaDefinition": {
    "kind": "xml",
    "fields": [{
      "name": "order", "path": "/order", "kind": "element", "type": "object",
      "namespaceUri": "", "repeated": false,
      "children": [{
        "name": "id", "path": "/order/id", "kind": "element", "type": "string",
        "namespaceUri": "", "repeated": false, "children": []
      }]
    }]
  }
}
```

IDOC returns the same tree shape with `format="IDOC"` and `kind="idoc-xml"`.
An example mappable path is `/ORDERS05/IDOC/E1EDK01/BELNR`. Attribute paths look like
`/order/@id`; namespace paths look like `/{urn:orders}order/{urn:orders}id`.
These are API field IDs, not XPath expressions. Changing a namespace prefix does
not change field identity. Repeated elements have `repeated=true`; observed child
fields are merged without array indexes.

CSV:

```json
{
  "id": 30, "schemaType": "SOURCE_TO_DEST_REQUEST", "format": "CSV",
  "mappingSupported": false, "schemaDefinition": {},
  "rawContent": "id;name\r\n42;Sample\r\n",
  "csvOptions": {"delimiter":";","hasHeader":true},
  "fileName":"sample.csv", "contentType":"text/csv", "fileSize":20,
  "sampleDownloadUrl":"/api/v1/integrations/10/mappings/schemas/30/sample"
}
```

Excel; size depends on the file:

```json
{
  "id": 30, "schemaType": "SOURCE_TO_DEST_REQUEST", "format": "EXCEL",
  "mappingSupported": false, "schemaDefinition": {}, "rawContent": null,
  "fileName": "orders.xlsx",
  "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "fileSize": 4096,
  "sampleDownloadUrl": "/api/v1/integrations/10/mappings/schemas/30/sample"
}
```

File bytes are not embedded in GET JSON or Base64. The UI should show mapping
controls based on `mappingSupported`. The backend also rejects CSV/Excel mapping ends.

## Mapping and completion

`POST .../mappings/fields` replaces the whole mapping list. XML/IDOC paths are
checked against the generated tree on save and completion. JSON paths are also
checked when source `samplePayload` or an independent target definition/sample
exists. Legacy source JSON without a sample has limited checks. AI job/append
validates normalized JSON/XML/IDOC paths, and suggestions also pass type/array
checks. Manual saves do not validate semantic correctness or runtime conversions.
A mapping with a CSV/Excel end returns 400 `MAPPING_NOT_SUPPORTED`.

If content changes remove a path used by a retained mapping, or a mapped record
is changed to CSV/Excel, the save returns 409 `SCHEMA_MAPPING_CONFLICT`. Remove
those mappings and save the list before changing content. If valid paths remain
but the tree/format changes, `reviewRequired=true`; use the standard review flow.
Failed changes keep old content/mappings. Successful content/file saves make the
integration DRAFT. Mappings to obsolete schemas that will be removed during
flow cleanup do not block the new save.

Completion checks environment, connection structure, review state, format matches,
nonempty JSON, valid XML/IDOC and its field tree, and the presence/readability of
CSV/Excel sample files. Field mappings are optional for every format. Response
records are optional for asynchronous flows, but are validated if present.

## Errors

Payload errors use the shared API error format. Every HTTP error has `code`, a
safe English `message`, `details`, `violations`, `params`, and `requestId`.
DTO/JSON parsing and authentication errors use the same contract.

| Example error | HTTP | `code` |
|---|---|---|
| Invalid XML / IDOC | 400 | `INVALID_XML` / `INVALID_IDOC` |
| Invalid CSV / Excel | 400 | `INVALID_CSV` / `INVALID_EXCEL` |
| Format/flow mismatch | 400 | `FORMAT_MISMATCH` |
| Unsupported field mapping | 400 | `MAPPING_NOT_SUPPORTED` |
| Size limit | 413 | `PAYLOAD_TOO_LARGE`; content checks may include `params.maxBytes` |
| Schema change breaks existing mappings | 409 | `SCHEMA_MAPPING_CONFLICT` |
| JSON field tree too large/complex | 422 | `TOO_MANY_FIELDS` / `INPUT_TOO_COMPLEX` |

The same content check can return 422 in AI input, or a violation under 409
`INTEGRATION_INCOMPLETE` during completion. Completion keeps `details` as English
fallback text. Clients select user text through coded `violations`. Parsing
errors do not return raw parser/exception messages.

[Error contract](error-codes.md), [full code/endpoint index](error-code-index.md),
and [response examples](error-catalog.json).

## Database upgrades and tests

Flyway's initial migration creates payload columns and format CHECK constraints
for all current formats, including EXCEL. Hibernate uses `ddl-auto=validate`.
Existing databases from the former Hibernate update setup must be reset for this
transition; see [database migrations](database-migrations.md). Future format or
column changes require a new migration, including any CHECK constraint updates.
`schema_definition` remains NOT NULL; CSV/Excel store an empty object.

Old records stored actual JSON objects even when the integration format was
XML/IDOC/CSV. They are not automatically converted to XML/CSV. Completion reports
a format mismatch until content is uploaded again in the correct format.

The normal test command is in the README. Real JSONB/TEXT/BYTEA storage, flow
cleanup, AI jobs, and system catalog tests are opt-in and skipped by default.
Create a **separate, empty PostgreSQL test database** and a `catalog_payload_test`
user with access only to that database. The test password is
`payload-test-only-password`; do not use it in production. Then run:

```sh
./mvnw -B '-Dtest=*PostgresTests' -Dcatalog.payload.postgres-test=true \
  -Dcatalog.payload.test-db-url=jdbc:postgresql://localhost:5432/integration_payload_test_20261025 test
```

Tests create tables using the application Flyway migrations and validate entity
compatibility with Hibernate. `FlywayMigrationPostgresTests` also checks clean
installation, migration replay prevention, and unmanaged schema rejection. Payload
roundtrip transactions roll back. Flow cleanup tests use independent transactions
to check commit/rollback and delete their own sample records afterward. AI database
tests check persistent jobs, results, approval/idempotency, expiry, and cascade
cleanup. The bootstrap test account may remain in the database.
