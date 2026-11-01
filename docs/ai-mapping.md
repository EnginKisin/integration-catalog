# AI field mapping assistant

AI is optional. It suggests field connections between every pair of JSON, XML,
and IDOC formats. It does not run integrations, generate transformation code, or
change current mappings automatically. The frontend is a separate project.
The backend selects the provider: local Ollama in Docker by default, or the
optional Google Gemini API. The frontend has no provider/model/API key selector;
both providers use the same endpoints.

## Suggestions and approval

While a job runs, the frontend polls at `pollAfterMs` intervals. Results are not
saved automatically. Saves are rejected if the context is old or connections conflict.

## Docker setup

This section covers Ollama. Gemini does not need a model/container download;
follow [Gemini setup](#gemini-setup) instead.

Normal `docker compose up` does not start Ollama. `.env.example` defaults to
`AI_ENABLED=false`. Developers who do not use AI do not need to download a model.

First prepare the database, admin, and encryption settings from the normal setup.
Before downloading a model, select Ollama and a local model in `.env`. The setup
service also uses `AI_MODEL`. It does not read backend settings, so the model name
must be set explicitly and must not be empty. Otherwise, setup stops with a clear
error. Do not run Ollama pull with a Gemini model name:

```dotenv
AI_ENABLED=true
AI_PROVIDER=ollama
AI_BASE_URL=http://ollama:11434
AI_MODEL=qwen3.5:4b
OLLAMA_IMAGE=ollama/ollama:0.40.13
```

Download the model with the separate setup container, which has internet access:

```sh
docker compose --profile ai-setup run --rm ollama-model-pull
```

`qwen3.5:4b` is about 3.3 GB. The Ollama image needs additional space. The model is
stored in `ollama_data`, not Git or the backend image. Before updating an existing
model installation, run `docker compose --profile ai stop ollama`. Do not run
setup and runtime services on the same volume at the same time. When the model
is ready, start the runtime profile:

```sh
docker compose --profile ai up --build -d --wait --wait-timeout 180
docker compose --profile ai ps
python3 scripts/check_ai_network.py
```

The runtime container connects only to the `internal: true` network `ai_internal`.
It exposes no host port and has no bind mount or Docker socket. The backend joins
this internal network. Ollama has `OLLAMA_NO_CLOUD=1`. Runtime does not depend on
the setup service, and the backend does not call a model download endpoint.

`check_ai_network.py` checks internal-only network access, no published ports,
disabled cloud use, failed outbound TCP connections to external IPs/domains, and
successful backend → Ollama access. This checks the runtime container; it does not
disable internet access for the Docker daemon or the whole computer. The backend
has another network. The local provider calls only the configured Ollama address.
AI requests do not send environment configs or credentials to the model.

The standard Ollama container on macOS Docker Desktop cannot use Apple GPU
acceleration. This setup runs on CPU. First load and suggestion generation can
take time. Runtime defaults are 6 GB RAM and 4 CPUs. Leave enough Docker Desktop
VM memory for the backend and database too.

## Configuration and providers

Backend defaults are in `application.properties`; `.env.example` shows example
values. An undefined optional backend AI variable uses its default. Remove its
line to use the default instead of leaving it empty. Compose manages Ollama image
selection and container limits.

| Setting | Default | Meaning |
|---|---|---|
| `AI_ENABLED` | `false` | Enable suggestion job creation |
| `AI_PROVIDER` | `ollama` | Backend choice: `ollama` or `gemini`; restart required |
| `AI_BASE_URL` | `http://ollama:11434` | Ollama origin only; Gemini ignores this setting |
| `AI_MODEL` | `qwen3.5:4b` | Selected provider's model ID; change it separately for Gemini |
| `GEMINI_API_KEY` | empty | Google API key kept only on the backend; not needed by Ollama |
| `AI_REQUEST_TIMEOUT_SECONDS` | `300` | Total model request timeout; maximum 600, excluding queue time |
| `AI_CONTEXT_TOKENS` | `8192` | Input planning budget; also sent to Ollama as `num_ctx` |
| `AI_MAX_OUTPUT_TOKENS` | `2048` | Model output limit; truncated output is not successful |
| `OLLAMA_IMAGE` | `ollama/ollama:0.40.13` | Same image for setup and runtime |
| `OLLAMA_MEMORY_LIMIT` | `6g` | Container memory limit |
| `OLLAMA_CPUS` | `4` | Container CPU limit |

Both providers return only short field IDs such as `S0` and `T0`, short mapping
reasons, and reason codes for unmatched fields. The backend converts IDs to allowed
field paths and adds readable explanations to reason codes. The frontend contract
stays the same. Existing/protected targets are removed from the model's candidates.
If all targets are protected, the model is not called. The backend still checks
types and array structures and does not suggest conflicting sources for one target.
IDoc technical attributes `BEGIN`/`SEGMENT` are excluded from source/target
candidates. They remain in unmatched fields and do not change manual mapping.
The model is not called if either side has no candidate business fields.

Ollama keeps a model in memory for 10 minutes after a request (`keep_alive` and
`OLLAMA_KEEP_ALIVE`). This can avoid reloading for the next job but continues to
use RAM. A 300-second CPU timeout is not a speed guarantee. Exceeding the output
limit returns `AI_OUTPUT_TRUNCATED`; lowering that limit alone does not make
responses faster.

`catalog.ai.max-fields=100` limits fields on each side. Normalized input is also
limited to 64 KiB. Context checks estimate input size and allow space for the
prompt/output. This is not an exact tokenizer count. Large pairs may return
`AI_CONTEXT_LIMIT`. A larger context can also need more memory.

`MappingSuggestionProvider` defines provider-independent input/output. Ollama and
Gemini adapters translate HTTP/model output into the shared contract.
`MappingSuggestionProtocol` shares the prompt, short IDs, and IDoc control filter.
`MappingSuggestionValidator` shares path, protected connection, type/array, and
confidence checks. `AiProviderHttpClient` shares timeouts, cancellation, a 256 KiB
response limit, and safe error handling. No OpenAI adapter is implemented.
There is no automatic provider/cloud fallback. API keys are not stored in the
frontend, job records, logs, or Git.

Backend readiness and manual mapping still work when AI is off or unavailable.

## Gemini setup

Gemini is an external service. When selected, the backend sends source/target
field names, paths, types, structure, and optional sample values over HTTPS to
Google. Samples are sent when `useSampleValues=true`. Setting it to false removes
sample values only; field metadata is still sent. Raw payload files and environment
config/credential records are not sent. The model is not asked to use tools or
web search. The Ollama runtime remains offline. The backend calls Gemini through
its own external network.

Backend `.env` settings:

```dotenv
AI_ENABLED=true
AI_PROVIDER=gemini
AI_MODEL=gemini-3.5-flash-lite
GEMINI_API_KEY=your-google-ai-studio-api-key
```

The key above is a placeholder. Do not put a real key in chat or frontend settings.
Use a model ID without the `models/` prefix. Check the [model list](https://ai.google.dev/gemini-api/docs/models)
and choose a text model available to your account that supports structured JSON
output. The default `AI_MODEL` does not switch to Gemini automatically.

```sh
docker compose up -d --build --wait app
```

This starts the database/backend; no `ai` or `ai-setup` profile is needed. To stop
an existing Ollama service, run `docker compose --profile ai stop ollama`. Its model
volume is kept. Gemini mode does not send requests to Ollama. To switch back, set
`AI_PROVIDER=ollama` and `AI_MODEL=qwen3.5:4b`, then restart the Ollama profile and
backend. Jobs queued before a provider/model change are not transferred to the
new provider; the worker ends them with `AI_CONFIGURATION_CHANGED`.

The adapter uses the fixed address
`https://generativelanguage.googleapis.com/v1beta/models/`. `AI_BASE_URL` cannot
redirect a Google key to another address. The key is sent in `x-goog-api-key`,
not the URL. Generation uses `:generateContent` and JSON Schema through
`responseJsonSchema`. Capability checks GET model metadata without generating
suggestions. Missing keys and invalid model IDs are rejected before queueing.
Key permissions, quota, and model access are checked during provider calls.

Gemini has [free and paid tiers](https://ai.google.dev/gemini-api/docs/pricing).
Quota, access, and data use terms depend on your Google account/tier. This project
does not enable a paid plan or guarantee free use. Quota errors do not cause
automatic retries or a switch to another provider.

## Provider-independent frontend

The frontend follows capability → create job → poll status → read result → append
after user approval. Do not add `provider`, `model`, `baseUrl`, or `apiKey` to job
requests. There is no frontend endpoint to change them. `provider` and `model` in
capability/result responses are information, not selection fields. API keys are
never returned there.

For `AI_RATE_LIMITED`, `AI_AUTHENTICATION_FAILED`, or `AI_API_KEY_MISSING`, show a
clear error and keep manual mapping available. The frontend must not switch
providers and retry automatically. POST/GET/result/append paths and successful
response formats are the same for both providers.

## Supported format pairs and source/target samples

Source and target can independently be JSON, XML, or IDOC. All nine pairs work
for direct and middleware request/response flows:

| Source → Target | JSON | XML | IDOC |
|---|---|---|---|
| JSON | Supported | Supported | Supported |
| XML | Supported | Supported | Supported |
| IDOC | Supported | Supported | Supported |

Here, IDOC means XML containing an `<IDOC>` element. Plain-text/fixed-width IDOC
files are not parsed. CSV/Excel are outside AI suggestions and append. Suggesting
fields across formats does not execute a payload transformation.

Extra fields are available on the existing schema save endpoint. IDs below are
examples. A direct flow has two independent sides in the same schema record:

```http
POST /api/v1/integrations/42/mappings/schemas
Content-Type: application/json
```

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "samplePayload": {
    "customerCode": "CUST-001",
    "fullName": "Alex Smith",
    "email": "demo@example.com"
  },
  "targetSamplePayload": {
    "clientId": "CUST-001",
    "displayName": "Alex Smith",
    "email": "demo@example.com"
  }
}
```

If `schemaDefinition` is omitted, `samplePayload` also fills the definition.
Standard JSON Schema `properties/items/type` can be sent as `schemaDefinition`.
Use `targetSchemaDefinition` for the target. Sample values are optional when field
definitions provide enough information. Without an independent target definition
or sample, direct-flow AI returns `SAMPLE_REQUIRED`.

XML/IDOC sources use `format` and `rawContent`. JSON sources use
`schemaDefinition` and/or `samplePayload`. Independent target fields:

| Target | Fields |
|---|---|
| JSON | `targetFormat: "JSON"`, `targetSchemaDefinition` and/or `targetSamplePayload` |
| XML | `targetFormat: "XML"`, `targetRawContent` |
| IDOC | `targetFormat: "IDOC"`, `targetRawContent` |

The backend builds an XML/IDOC target's `targetSchemaDefinition`; it does not accept
that tree from the client. Source/target formats must match the integration sides.
In a direct response schema, direction is reversed: `format` is the integration's
target format, and `targetFormat` is its source format. Legacy JSON clients can
omit the target format; new clients should send it explicitly.

JSON → XML example:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "samplePayload": {"customerCode": "000123", "fullName": "Alex Smith"},
  "targetFormat": "XML",
  "targetRawContent": "<Customer><ClientId>000123</ClientId><DisplayName>Alex Smith</DisplayName></Customer>"
}
```

For JSON → IDOC, change the target side to:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "samplePayload": {"customerCode": "000123", "fullName": "Alex Smith"},
  "targetFormat": "IDOC",
  "targetRawContent": "<DEBMAS07><IDOC BEGIN=\"1\"><E1KNA1M SEGMENT=\"1\"><KUNNR>000123</KUNNR><NAME1>Alex Smith</NAME1></E1KNA1M></IDOC></DEBMAS07>"
}
```

IDOC → JSON example:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "IDOC",
  "rawContent": "<DEBMAS07><IDOC BEGIN=\"1\"><E1KNA1M SEGMENT=\"1\"><KUNNR>000123</KUNNR><NAME1>Alex Smith</NAME1></E1KNA1M></IDOC></DEBMAS07>",
  "targetFormat": "JSON",
  "targetSamplePayload": {"clientId": "000123", "displayName": "Alex Smith"}
}
```

XML → JSON uses the same request shape with source `format: "XML"` and `rawContent`.
For XML ↔ IDOC, each side contains raw content in its own format.

Middleware already has separate source/target schema records. Each uses
`schemaDefinition` and/or `samplePayload` for JSON, or `rawContent` for XML/IDOC.
`targetFormat`, `targetRawContent`, `targetSamplePayload`, and
`targetSchemaDefinition` are for direct flows only. Allowed pairs:

- Direct: the same `SOURCE_TO_DEST_REQUEST` or the same `DEST_TO_SOURCE_RESPONSE` record.
- Middleware request: `SOURCE_TO_MW_REQUEST` → `MW_TO_DEST_REQUEST`.
- Middleware response: `DEST_TO_MW_RESPONSE` → `MW_TO_SOURCE_RESPONSE`.

JSON samples must be objects. Nested objects/arrays are supported. JSON
normalization uses up to 32 levels, three sample items per array, and 128-character
values. XML/IDOC parsing blocks DTD and external entities. Its limits are 64 levels,
10000 nodes, and 1 MiB of text per side. AI scalar field counts are also limited
by `max-fields`. Namespaces, attributes, repeated segments, and up to three sample
values are included in the shared field profile.

JSON AI paths use JSON Pointer: `/customer/id`, `/lines/*/sku`. `*` means an array
item. `/` and `~` in JSON keys are escaped as `~1` and `~0`. Simple legacy paths
such as `customer.id` / `lines[0].sku` are normalized as aliases. New clients should
use canonical paths. Types inferred from one sample are not a contract for every
possible real value.

XML/IDOC uses full paths from the existing field tree, not JSON Pointer:
`/Customer/ClientId`, `/DEBMAS07/IDOC/E1KNA1M/KUNNR`, `/{urn:customer}Customer/@code`.
Paths are kept even if a namespace URI includes slashes. Fields under repeated
XML segments have `array=true`; their paths do not get JSON `*`. Without XML/XSD
type information, scalar types are `unknown`, and samples stay as text. Codes such
as `000123` are not converted to numbers. No XSD or external dictionary is
downloaded. Raw source/target XML samples and target format are part of the context
revision. Changing only a sample value also makes an older suggestion stale.

## Frontend services

Shared root: `/api/v1/integrations/{integrationId}/mappings`.
GET requires VIEWER/EDITOR/ADMIN. POST requires EDITOR/ADMIN and CSRF.
POST also needs a 1–100 character `Idempotency-Key` header.

| Action | Endpoint | Success response |
|---|---|---|
| Read context | GET `/context` | 200 `{contextRevision,schemas,mappings}` |
| Check availability | GET `/ai/capabilities` | 200 `{enabled,available,provider,model,supportedFormats,maxFieldsPerSide,code}` |
| Create suggestion job | POST `/ai/jobs` | 202 job body + `Location` |
| Read status | GET `/ai/jobs/{jobId}` | 200 job body |
| Get result | GET `/ai/jobs/{jobId}/result` | 200 result body |
| Append selected connections | POST `/fields/append` | 200 `{contextRevision,addedMappings}` |

First get the current revision from `/context`. It comes from schemas, samples,
the flow, and current mappings; the client does not calculate it. Direct flows
use the same ID for both schemas.

```json
{
  "sourceSchemaId": 101,
  "targetSchemaId": 101,
  "expectedContextRevision": "context-yanitindaki-gercek-deger",
  "protectedMappings": [],
  "options": {"useSampleValues": true}
}
```

Send this body to POST `/ai/jobs`. The backend protects saved mappings.
`protectedMappings` adds unsaved screen connections with the same schema IDs and
field paths. Samples are used if `options` is omitted. `useSampleValues=false`
sends only field structure/types to the model; it does not delete previously
saved samples from the database.

The job body has `jobId,status,createdAt,expiresAt,errorCode,message,resultUrl,pollAfterMs`.
Possible states are `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, and `EXPIRED`.
`pollAfterMs=2000` for QUEUED/RUNNING and null for terminal states. Job dates are
UTC `Instant` values in ISO 8601 format. Use `resultUrl` after SUCCEEDED.
Creating a job does not change schemas/mappings. A provider failure after a job
is accepted appears as HTTP 200 with `status=FAILED` on the status endpoint.

Do not wait for suggestions through one long HTTP request. POST quickly returns
202. Poll with short GET requests at `pollAfterMs` intervals. Do not mark a RUNNING
job failed just because a client timer reaches 60/180 seconds. The default model
timeout is 300 seconds, plus queue time. You can resume polling the same `jobId`
after leaving the screen.

Result example:

```json
{
  "jobId": "is-yanitindaki-id",
  "contextRevision": "is-olusturulurken-alinan-surum",
  "stale": false,
  "provider": "ollama",
  "model": "qwen3.5:4b",
  "suggestions": [
    {
      "suggestionId": "backendin-urettigi-id",
      "sourceSchemaId": 101,
      "sourceFieldPath": "/customerCode",
      "targetSchemaId": 101,
      "targetFieldPath": "/clientId",
      "confidence": "HIGH",
      "reason": "Both fields identify the same customer."
    }
  ],
  "unmatchedSourceFields": [],
  "unmatchedTargetFields": [],
  "warnings": []
}
```

HIGH/MEDIUM are not calibrated accuracy percentages. Numeric `aiConfidenceScore`
stays null. Uncertain fields or fields needing transformation should stay
unmatched. Unmatched records contain `fieldPath,code,reason`. Codes include
`AMBIGUOUS`, `NO_COMPATIBLE_FIELD`, `TYPE_MISMATCH`, `INSUFFICIENT_CONTEXT`,
`TRANSFORMATION_REQUIRED`, and `NO_CONFIDENT_MATCH`. Zero suggestions is a valid
successful result. Render model explanations as plain text.

Show suggestions as temporary frontend connections. The user edits, removes,
and selects them, then sends the choices to POST `/fields/append`:

```json
{
  "expectedContextRevision": "is-olusturulurken-alinan-surum",
  "aiJobId": "is-yanitindaki-id",
  "mappings": [
    {
      "suggestionId": "backendin-urettigi-id",
      "sourceSchemaId": 101,
      "sourceFieldPath": "/customerCode",
      "targetSchemaId": 101,
      "targetFieldPath": "/clientId"
    }
  ]
}
```

Send only new connections. The service keeps current IDs and appends records.
A target can have one connection; a source can feed several targets. A conflict,
invalid path, or old revision rejects the whole batch. Successful append makes
the integration `DRAFT` and does not clear `reviewRequired` automatically.

Saving the same paths gives `origin=AI_ACCEPTED,isAiGenerated=true`. Editing paths
gives `origin=AI_EDITED,isAiGenerated=false`. `aiJobId` and `aiSuggestionId` keep the
origin link. The backend sets these values. Omit `suggestionId` for a new manual
connection, and omit `aiJobId` for a fully manual append. Manual append works with
AI disabled.

The same key and body return the first response; a different body returns 409.
A replay is the original save receipt, not the latest state. Read `/context`
after later edits. Save all selections in one approval; the first save changes
the context revision. For `stale=true` or `CONTEXT_CHANGED`, refresh the context
and create a new job.

Existing POST `/fields` still replaces the whole list. Do not send AI suggestions
directly there. Unchanged existing AI connections keep their origin when resent.
Clients cannot mark a new manual connection as AI-generated.

## Errors and job lifecycle

API errors, fixed mapping explanations, and warnings are English. The model is
asked to write suggestion reasons in English. The frontend localizes by code.
The [shared error contract](error-codes.md), [endpoint/code index](error-code-index.md),
and [examples for every code](error-catalog.json) cover HTTP errors, job `errorCode`,
and capability `code` in one catalog.

HTTP 429 `AI_QUEUE_FULL` includes `Retry-After: 5`, an advisory queue delay.
Provider quota errors become job `AI_RATE_LIMITED`; the status GET can return
HTTP 200 without Retry-After. Expired jobs have `errorCode=AI_RESULT_EXPIRED`.
Use `requestId` for HTTP errors, `X-Request-ID` on all responses, and `jobId` for
the persistent AI operation.

| HTTP / state | Codes / behavior |
|---|---|
| 400 | `MISSING_REQUEST_HEADER`, `INVALID_IDEMPOTENCY_KEY`, DTO validation errors |
| 401 / 403 | Missing session, role, or CSRF |
| 404 | Integration, schema, or a job within this integration was not found |
| 409 | `CONTEXT_CHANGED`, `MAPPING_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `AI_RESULT_NOT_READY` |
| 410 | `AI_RESULT_EXPIRED` |
| 413 | `AI_INPUT_TOO_LARGE`, `AI_CONTEXT_LIMIT` |
| 422 | `AI_FORMAT_NOT_SUPPORTED`, `INVALID_SCHEMA_PAIR`, `FORMAT_MISMATCH`, `SAMPLE_REQUIRED`, `FIELD_PATH_NOT_FOUND`, `INVALID_SUGGESTION`, XML/IDOC validation, and complexity/field limits |
| 429 | `AI_QUEUE_FULL` |
| 503 | `AI_DISABLED`, `AI_PROVIDER_NOT_SUPPORTED`; Gemini: `AI_API_KEY_MISSING` / `AI_CONFIGURATION_INVALID` |
| Job `FAILED` | `AI_UNAVAILABLE`, `AI_MODEL_NOT_INSTALLED`, `AI_TIMEOUT`, `AI_INVALID_OUTPUT`, `AI_OUTPUT_TRUNCATED`, `AI_INTERRUPTED`, `AI_CONFIGURATION_CHANGED`, `AI_CONFIGURATION_INVALID`, `AI_CLOUD_MODEL_FORBIDDEN`, `AI_API_KEY_MISSING`, `AI_AUTHENTICATION_FAILED`, `AI_MODEL_NOT_AVAILABLE`, `AI_RATE_LIMITED`, `AI_CONTENT_BLOCKED`, `AI_INTERNAL_ERROR` |

Ollama capability `code=READY` means the model is installed. Run the separate setup
step for `AI_MODEL_NOT_INSTALLED`. Gemini READY means the key can read model
metadata and the model supports `generateContent`; it does not guarantee quota or
later generation success. Model health does not affect overall readiness.

Gemini 401/403 becomes `AI_AUTHENTICATION_FAILED`; 404 becomes
`AI_MODEL_NOT_AVAILABLE`; 429 becomes `AI_RATE_LIMITED`; 400/422 becomes
`AI_CONFIGURATION_INVALID`; 408/504 becomes `AI_TIMEOUT`; other provider failures
become `AI_UNAVAILABLE`. Raw provider/model responses are not exposed to users or
logs. Safety blocks become `AI_CONTENT_BLOCKED`. MAX_TOKENS output becomes
`AI_OUTPUT_TRUNCATED`. Partial mappings are not saved as successful results.

The queue is persistent in PostgreSQL. The worker releases its database lock
before calling the model on another thread. One backend instance runs one model
request at a time, with a default queue limit of 10. Across multiple instances,
`SKIP LOCKED` prevents claiming the same job twice, but each instance has its own
worker. This release targets local single-instance use. An interrupted RUNNING
job becomes FAILED after its lease expires. Model calls are not retried automatically.

For `AI_TIMEOUT`, first check job status. FAILED with AI_TIMEOUT means the backend
model timeout expired. If it is still RUNNING, check the frontend wait limit and
polling. Recreate the app container to apply new settings. Old failed jobs do not
restart by themselves. Create a new job with a new `Idempotency-Key`; reusing the
old key returns the existing failed job.

Jobs expire after 24 hours by default. Raw normalized input is cleared after
success/failure. Expiry also clears input/result JSON. Cleanup runs even when AI
is off. Status/idempotency metadata and append receipts stay until the integration
is deleted. Deleting it removes jobs/receipts through database cascade rules.
Saved schema samples are not part of this temporary job cleanup.

## Validation

```sh
./mvnw -B '-Dtest=*Tests,!IntegrationCatalogApplicationTests' test
python3 scripts/check_repository.py
python3 scripts/check_ai_network.py
python3 scripts/smoke_ai_mapping.py
```

The smoke script logs in with admin settings from local `.env`/environment and
creates a temporary integration with independent samples. It checks that manual
connections are kept, real model suggestions are produced, approval saves work,
and idempotency/old-context checks work. It deletes its integration afterward
and does not add data to existing records. It also checks expected customer code
and name mappings; a nonempty suggestion list alone does not pass the smoke test.
The script uses the selected backend provider. With Gemini, synthetic samples are
sent to Google and account quota/pricing applies. Automated `GeminiProviderTests`
use mocked HTTP without a key; they do not validate live Google access, account
quota, or real model quality.

`AiMappingPostgresTests` uses the same `-Dcatalog.payload.postgres-test=true` flag
as the other optional PostgreSQL tests. Run it only on a separate test database.
Normal tests use fake providers; CI does not download models or call cloud
services. Flyway's initial migration creates AI tables, indexes, and cascade
constraints; Hibernate validates the schema. Start this transition with an empty
database and use [versioned migrations](database-migrations.md) for later changes.

The live model smoke script accepts format options:

```sh
python3 scripts/smoke_ai_mapping.py --source-format JSON --target-format IDOC
python3 scripts/smoke_ai_mapping.py --source-format XML --target-format JSON
python3 scripts/smoke_ai_mapping.py --matrix
```

`--matrix` runs all nine pairs in order. It creates a temporary integration for
each pair, keeps the existing manual connection, approves an AI suggestion, and
removes the fixture. The full matrix can take several minutes on CPU.

### Live test coverage

Automated tests cover direct/middleware request/response directions, all nine
format pairs, namespace/attribute paths, repeated items, and XML external resource
blocking. Provider HTTP tests use fake responses. They do not measure real model
quality or Google account quota.

Local measurements from October 31, 2026 include polling and save checks:

| Provider / environment | Sample | Observed time |
|---|---|---|
| Ollama, Apple M4 / 16 GB, CPU/Docker, `qwen3.5:4b` | Small JSON/JSON, JSON/IDoc, IDoc/JSON | 20.2–40.6 seconds |
| Same Ollama setup | 15-field JSON/XML | 170.2 seconds |
| Google Gemini `gemini-3.5-flash-lite` | Small JSON/JSON, JSON/XML, JSON/IDoc | About 2.1 seconds each |

Temporary samples verified customer code/name mappings, keeping the manual email
connection, approval saves, idempotency, and old-context rejection. A live matrix
of all nine pairs was also run for Ollama with the previous protocol. The pairs
above were checked again with the latest protocol. Scripts clean up their own
records. These measurements are not latency or business-field accuracy guarantees.
Model load, schema size, and hardware affect results.

Official sources: [Gemini content generation API](https://ai.google.dev/api/generate-content),
[Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output),
[Gemini model list](https://ai.google.dev/gemini-api/docs/models),
[Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing),
[Ollama Docker](https://docs.ollama.com/docker),
[local/cloud settings](https://docs.ollama.com/faq),
[Docker internal networks](https://docs.docker.com/reference/compose-file/networks/#internal),
[Qwen model license](https://huggingface.co/Qwen/Qwen3.5-4B/blob/main/LICENSE).
