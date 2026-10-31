# Creating and editing integrations

An integration starts as `DRAFT`. Save environment settings, schemas, and optional
field mappings. When the user requests completion, the backend checks every
completion rule again. Success changes the status to `COMPLETED`. This means the
catalog record is complete; it does not run the integration.

All writes below need an EDITOR/ADMIN session and a CSRF header. Credential writes
also need the [secret permissions](authentication.md).

## Minimal example: direct, asynchronous

1. Use `GET /api/v1/systems?active=true` to choose source and target systems from
   the shared catalog. Choose middleware from the same list when needed. Create
   a system with `POST /api/v1/systems` if necessary:

```json
{"name":"ERP","description":"Order system"}
```

2. Send this body to `POST /api/v1/integrations`. The example assumes source ID 1
   and target ID 2. Use the returned integration ID for later steps.

```json
{
  "name":"Order transfer",
  "description":"Definition for transferring ERP orders to the target system",
  "sourceSystemId":1,
  "destinationSystemId":2,
  "middlewareSystemId":null,
  "sourcePayloadFormat":"JSON",
  "destinationPayloadFormat":"JSON",
  "isSync":false,
  "hasMiddleware":false
}
```

3. If the new integration ID is 10, call `POST /api/v1/integrations/10/configs`:

```json
[
  {
    "configTarget":"DESTINATION",
    "environmentType":"DEV",
    "url":"https://destination.example.com/orders",
    "timeoutMs":5000,
    "authType":"NONE",
    "retryCount":0,
    "retryIntervalMs":0
  }
]
```

The backend does not send a request to this URL. Timeout/retry settings are
catalog metadata. This endpoint replaces the whole config list. If the next
save includes only TEST, DEV is deleted. Do not send a duplicate environment/target pair.

4. Call `POST /api/v1/integrations/10/mappings/schemas`:

```json
{
  "schemaType":"SOURCE_TO_DEST_REQUEST",
  "format":"JSON",
  "schemaDefinition":{
    "type":"object",
    "properties":{
      "orderId":{"type":"string"}
    }
  },
  "samplePayload":{"orderId":"ORD-001"},
  "targetFormat":"JSON",
  "targetSamplePayload":{"externalOrderId":"ORD-001"}
}
```

This example uses JSON. `schemaDefinition` is a JSON object, but the full JSON
Schema standard is not validated. You can provide `samplePayload` instead; it
also becomes the definition. See the [payload guide](payload-content.md#mapping-and-completion)
for path checks and independent targets.

For XML/IDOC, send `format` + `rawContent`; the backend builds the field tree.
CSV/Excel store sample content/files only and do not support field mapping.
[File upload, read, and format rules](payload-content.md).

5. Field mapping is optional in this example. Send
   `PATCH /api/v1/integrations/10/complete` without a body. Success returns 200
   with an empty body. Read `status=COMPLETED` with `GET /api/v1/integrations/10`.

## Environment and schema rules

At least one environment configuration is required. You do not need all of DEV,
TEST, and PROD. Each saved environment needs DESTINATION. With middleware, the
same environment also needs MIDDLEWARE. There is no SOURCE config target.

| Flow | Required schema types |
|---|---|
| Direct, asynchronous | `SOURCE_TO_DEST_REQUEST` |
| Direct, synchronous | `SOURCE_TO_DEST_REQUEST`, `DEST_TO_SOURCE_RESPONSE` |
| Middleware, asynchronous | `SOURCE_TO_MW_REQUEST`, `MW_TO_DEST_REQUEST` |
| Middleware, synchronous | `SOURCE_TO_MW_REQUEST`, `MW_TO_DEST_REQUEST`, `DEST_TO_MW_RESPONSE`, `MW_TO_SOURCE_RESPONSE` |

Posting the same integration/schema type again updates the existing schema and
keeps its ID. JSON can save an empty object, but completion rejects it. XML/IDOC
must be valid for parsing. CSV/Excel need a valid sample file; CSV can also be
submitted as text. Every record's format must match its side of the flow.
Direct and middleware schema types cannot be mixed. Response schemas are optional
for asynchronous flows, but response schemas for the same connection structure
may exist. After adding/removing middleware, the first successful sample save
for the new flow removes incompatible old schemas and their mappings in the same
transaction. Failed saves keep old data. This applies to JSON/text and file uploads.

## Field mappings

Read real schema IDs with `GET /api/v1/integrations/10/mappings/schemas`.
`POST /api/v1/integrations/10/mappings/fields` replaces the whole mapping list;
`[]` removes all connections. Mapping IDs may change on another save.

For a direct request schema with ID 30:

```json
[
  {
    "sourceSchemaId":30,
    "sourceFieldPath":"/orderId",
    "targetSchemaId":30,
    "targetFieldPath":"/externalOrderId",
    "isAiGenerated":false,
    "aiConfidenceScore":null
  }
]
```

Both schemas must belong to the same integration. Middleware completion allows
`SOURCE_TO_MW_REQUEST → MW_TO_DEST_REQUEST` and
`DEST_TO_MW_RESPONSE → MW_TO_SOURCE_RESPONSE`. In direct flows, both ends use the
same schema type; do not connect a request schema to a response schema.

Field paths must not be empty. XML/IDOC paths are checked against the field tree.
Legacy JSON records with a single definition have limited path checks. Independent
samples and the AI/append flow check paths. Runtime type conversion is not
validated. CSV/Excel cannot be mapping endpoints. `isAiGenerated` is required;
legacy manual saves store false and a null score for new manual connections.
Unchanged AI connections keep their origin. The optional [AI suggestion and approval flow](ai-mapping.md)
uses separate endpoints.

## Editing a completed record

`PUT /api/v1/integrations/{id}` is not a partial update. Send every required field
from the create body. Do not send `status`, `reviewRequired`, or dates.

| Action | Result |
|---|---|
| Only integration name/description changes | Current status is kept |
| System IDs, formats, synchronous mode, or middleware use changes | `DRAFT`; `reviewRequired=true` if related data exists |
| Config, schema, or field mapping is saved | `DRAFT`; a pending review is not cleared automatically |
| Schema tree/format changes while mappings are kept | `DRAFT`, `reviewRequired=true`; changes that break mappings return 409 |
| Shared system name/description or active status changes | Integration status and links are kept |
| Review is confirmed | Only `reviewRequired=false`; completion is not automatic |

A technical PUT keeps configs, secrets, schemas, and mappings. If the flow changes,
the first successful current sample save removes incompatible schemas/mappings.
The user reviews remaining data for the new choices. Confirm with
`POST /api/v1/integrations/{id}/review/confirm` and `{"confirmed":true}`, then call
complete. Review approval does not mean the backend has fully checked content compatibility.

Missing/inconsistent data or a pending review returns 409 on completion. Localize
the `violations` codes and parameters in `code=INTEGRATION_INCOMPLETE` responses;
`details` is English fallback text. See the [error contract](error-codes.md).
Completion does not delete data or fill in missing information. Old incompatible
schemas are removed on the first successful current sample save. Reload schemas
and fields afterward. There is no single-schema delete endpoint.

## Deletion

ADMIN can DELETE an integration and its related configs, schemas, and mappings.
Shared catalog systems remain. An in-use catalog system cannot be deleted;
deactivate it instead. See [catalog management](systems-frontend.md).
