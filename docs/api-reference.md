# API contract and endpoint index

The local API root is `http://localhost:8080/api/v1`. Paths below are relative to
this root. JSON writes use `Content-Type: application/json`; login uses form data.
Successful responses do not have a shared `data` wrapper. All changes require CSRF.
See [authentication](authentication.md) for roles and credential permissions.

## Authentication and users

| Method / path | Success response | Access |
|---|---|---|
| GET `/auth/csrf` | 200 `{headerName,parameterName,token}` | Public |
| POST `/auth/login` | 200 empty | Public + CSRF |
| POST `/auth/logout` | 204 empty | CSRF |
| GET `/auth/me` | 200 profile | Session |
| GET `/users?page=0&size=20` | 200 `{items,page,size,totalElements,totalPages}` | ADMIN |
| POST `/users` | 201 user | ADMIN |
| PUT `/users/{id}/active` | 200 user | ADMIN |
| PUT `/users/{id}/role` | 200 user | ADMIN |
| PUT `/users/{id}/permissions` | 200 user | ADMIN |
| POST `/users/{id}/invitations/resend` | 202 empty | ADMIN |
| POST `/users/{id}/password-reset` | 202 empty | ADMIN |
| POST `/users/{id}/sessions/revoke` | 204 empty | ADMIN |
| GET `/users/mail-status` | 200 `{pending,failed}` | ADMIN |
| POST `/auth/invitations/accept` | 204 empty | Valid token |
| POST `/auth/password-reset/request` | 202 `{message}` | Public |
| POST `/auth/password-reset/confirm` | 204 empty | Valid token |
| POST `/auth/password/change` | 204 empty | Session + current password |
| POST `/auth/email/change` | 202 empty | Session + current password |
| POST `/auth/email/confirm` | 204 empty | Valid token |

User responses contain `id, username, email, emailVerified, active,
passwordResetRequired, role, permissions`. There is no single-user detail GET,
public signup, or user DELETE endpoint. The user list is ordered by ID. Pages
start at zero and size is 1–100. See [user management](user-management.md) for
request bodies and token lifetimes.
Login/logout are handled by filters. Do not expect them to appear as controller
endpoints in Swagger, or expect the Swagger Authorize button to log you in.
`GET /auth/me` returns `username,email,emailVerified,active,authorities,permissions`.
Authority values have the `ROLE_` prefix, such as `ROLE_ADMIN`.

## Shared catalogs

The shared catalog is under `/systems`. Each integration assigns the source,
target, or middleware role to a system.

| Method / path | Success response | Access |
|---|---|---|
| GET `/systems` or `/systems?active=true/false` | 200 DTO array | VIEWER / EDITOR / ADMIN |
| GET `/systems/{id}` | 200 DTO | VIEWER / EDITOR / ADMIN |
| POST `/systems` | 201 DTO | EDITOR / ADMIN |
| PUT `/systems/{id}` | 200 DTO | EDITOR / ADMIN |
| PATCH `/systems/{id}/active` | 200 DTO | EDITOR / ADMIN |
| DELETE `/systems/{id}` | 204 empty | ADMIN; usage must be zero |

DTO: `id,name,description,active,usageCount`. POST/PUT body: `{name,description}`;
PATCH body: `{active}`. Lists are not paginated. Without an active filter, inactive
systems are included. The frontend must not assume names or descriptions are unique.

## Integrations and mappings

| Method / path | Success response | Access |
|---|---|---|
| GET `/integrations?keyword=...` | 200 DTO array | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}` | 200 DTO | VIEWER / EDITOR / ADMIN |
| POST `/integrations` | 201 DTO | EDITOR / ADMIN |
| PUT `/integrations/{id}` | 200 DTO | EDITOR / ADMIN |
| DELETE `/integrations/{id}` | 204 empty | ADMIN |
| POST `/integrations/{id}/configs` | 200 empty | EDITOR / ADMIN; secret changes need extra permission |
| POST `/integrations/{id}/review/confirm` | 200 DTO | EDITOR / ADMIN |
| PATCH `/integrations/{id}/complete` | 200 empty | EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/schemas` | 200 schema array | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/schemas` | 200 schema | EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/schemas/upload` | 200 schema; multipart metadata + file | EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/schemas/{schemaId}/sample` | 200 file; 404 if absent | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/fields` | 200 mapping array | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/fields` | 200 empty | EDITOR / ADMIN |
| GET `/integrations/{id}/configs/{environment}/{target}/credentials` | 200 `{authCredentials}` | ADMIN or VIEW_CREDENTIALS |

## AI suggestions and safe append

| Method / path | Success response | Access |
|---|---|---|
| GET `/integrations/{id}/mappings/context` | 200 `{contextRevision,schemas,mappings}` | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/ai/capabilities` | 200 availability / provider / formats | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/ai/jobs` | 202 job; `Idempotency-Key` required | EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/ai/jobs/{jobId}` | 200 job status | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/ai/jobs/{jobId}/result` | 200 suggestions / unmatched fields | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/fields/append` | 200 added connections / new context; `Idempotency-Key` required | EDITOR / ADMIN |

Job/append requests include the expected `contextRevision`. An old context returns
409; a result that is not ready returns 409; an expired result returns 410. If an
AI job fails, the status GET returns 200 with `status=FAILED`. See the [AI contract](ai-mapping.md)
for full requests, responses, and error codes. Provider/model selection and the
Gemini key are backend settings; the frontend cannot select them in a request.

## Catalog and mapping response fields

Integration DTO fields: `id,name,description,sourceSystemId,sourceSystemName,
destinationSystemId,destinationSystemName,middlewareSystemId,middlewareSystemName,
sourcePayloadFormat,destinationPayloadFormat,isSync,hasMiddleware,status,
reviewRequired,createdAt,updatedAt,environmentTypes,environmentConfigs`.
Names for all three system roles come from the shared catalog. Schemas and field
mappings are not included in list/detail responses. `environmentTypes` lists each
configured environment once; it is `[]` when no settings exist.
Search matches integration names and system names in all three roles, ignoring
letter case. The result array has no pagination or client sorting parameter.

Config response: `id,configTarget,environmentType,url,timeoutMs,authType,
credentialsConfigured,retryCount,retryIntervalMs`. It does not contain `authCredentials`.
Schema: `id,schemaType,format,schemaDefinition,samplePayload,targetSchemaDefinition,targetSamplePayload,targetFormat,targetRawContent,rawContent,mappingSupported,csvOptions,
fileName,contentType,fileSize,sampleDownloadUrl`. See [payloads](payload-content.md)
for format content, file uploads, and error examples. Field mapping:
`id,sourceSchemaId,sourceFieldPath,targetSchemaId,targetFieldPath,isAiGenerated,aiConfidenceScore,origin,aiJobId,aiSuggestionId`.
See [AI mapping](ai-mapping.md) for optional AI services, independent JSON/XML/IDOC
sides, and the append contract. Ollama/Gemini selection is a backend setting.
The frontend does not send a provider, model, or API key. Both providers use the
same AI endpoints and response format.

Config and legacy `/mappings/fields` POST bodies are JSON arrays that replace the
whole current list. Missing items are deleted; `[]` clears the list. Configs match
by environment/target pair. The service filters out MIDDLEWARE configs when the
integration has no middleware; clients should not send them. Schema POST creates
or updates by schema type. There is no schema DELETE or single config/field PATCH.
The first successful schema POST/upload after a flow change removes schemas from
the old flow and their mappings in the same transaction as the new record.
Failed operations keep the old data. Reload schemas and fields after a successful
save. See the [workflow guide](integration-workflow.md) for full bodies and required schemas.

See the [frontend system contract](systems-frontend.md) for sample bodies, the map,
and the environment filter.

## Enum values

| Field | Values |
|---|---|
| `sourcePayloadFormat`, `destinationPayloadFormat` | `JSON`, `XML`, `IDOC`, `CSV`, `EXCEL` |
| `environmentType`, environment in credential paths | `DEV`, `TEST`, `PROD` |
| `configTarget`, target in credential paths | `DESTINATION`, `MIDDLEWARE` |
| `authType` | `NONE`, `BASIC_AUTH`, `OAUTH2`, `API_KEY` |
| `status` | `DRAFT`, `COMPLETED`; set by the backend |
| `schemaType` | `SOURCE_TO_MW_REQUEST`, `MW_TO_DEST_REQUEST`, `DEST_TO_MW_RESPONSE`, `MW_TO_SOURCE_RESPONSE`, `SOURCE_TO_DEST_REQUEST`, `DEST_TO_SOURCE_RESPONSE` |
| `role` | `ADMIN`, `EDITOR`, `VIEWER` |
| `permissions` | `VIEW_CREDENTIALS`, `MANAGE_CREDENTIALS` |

## Main field validation rules

| Field | Rule |
|---|---|
| Catalog / integration `name` | Required, maximum 100 characters |
| `description` | Must not be null, maximum 500 characters; an empty string is allowed |
| Basic integration fields | sourceSystemId and destinationSystemId are required positive system IDs; formats, isSync, and hasMiddleware are required |
| `middlewareSystemId` | Positive ID when hasMiddleware=true; null or absent when false |
| Config environment/target/authType | Required enum |
| `url` | Required, maximum 100 characters; HTTP URL syntax is not validated |
| `timeoutMs` | If supplied, > 0; milliseconds |
| `retryCount`, `retryIntervalMs` | If supplied, >= 0; interval is in milliseconds |
| `authCredentials` | Dynamic JSON map; a new value must not be empty; required keys are not checked by auth type |
| JSON source content | Needs a `schemaDefinition` or `samplePayload` object. A sample alone also becomes the definition. Completion needs a nonempty definition. The backend creates `schemaDefinition` for other formats |
| `format`, `rawContent`, `csvOptions`, file | [Format contract and limits](payload-content.md) |
| Field paths | Required, maximum 500 characters |
| Field mapping `isAiGenerated` | Required in legacy list saves; false for new manual connections; unchanged AI connections keep their origin |
| Field mapping `aiConfidenceScore` | Null for new manual/AI records; AI results use HIGH/MEDIUM confidence levels |
| Username | 1–100 characters, `[a-zA-Z0-9._-]+`, case-sensitive |
| Email | Valid email, maximum 254 characters; normalized and stored in lowercase |
| New password | 12–128 characters; must not contain only spaces |
| Account link `token` | URL-safe Base64, 43 characters; `[A-Za-z0-9_-]{43}` |

For a JSON upload, `schemaDefinition` comes from the file. XML/IDOC returns a field
tree; CSV/Excel returns `{}`. See the [payload guide](payload-content.md) for text
and file rules. Save checks and completion checks differ. For example, JSON `{}`
can be saved but not completed; completion rejects an invalid mapping flow pair.

## Error contract

API errors use the shared `ErrorResponse` format. Every error includes `code`,
a safe English `message`, `details: string[]`, `violations`, `params`, and `requestId`.
The frontend selects user text from `code` and `violations[].code`; it does not
parse explanation text. Every request receives `X-Request-ID`. HTTP 429 responses
include `Retry-After` as a positive number of seconds.

- [Shared contract, field errors, and examples](error-codes.md)
- [Code, HTTP status, and endpoint index](error-code-index.md)
- [JSON catalog with examples for each code and context](error-catalog.json)

DTO/parameter validation returns 400 `VALIDATION_FAILED`. Completion errors return
409 `INTEGRATION_INCOMPLETE` with coded `violations`. `details` remains as English
fallback text, and empty lists are also returned. Authentication, authorization,
CSRF, framework, and rate-limit errors use the same format. Unrecognized routes
are denied by security; an unknown URL does not always return 404.

A failed AI job is different from a failed status request. A job GET can return
HTTP 200 with `status=FAILED` and an `errorCode`. Health endpoints are outside
`/api/v1`, under `/actuator/health`, and keep their own health format.
[Health contract](configuration.md#health-checks).
