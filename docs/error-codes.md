# API errors

The backend owns stable error codes and safe English fallback explanations. Clients own user-facing text and localization. Never parse `message` or `details` to determine behavior.

- [Code, HTTP status and endpoint index](error-code-index.md)
- [Machine-readable catalog with examples for every code and context](error-catalog.json)
- [Endpoint reference](api-reference.md)

## Shared HTTP error envelope

Application-generated API errors, including authentication, authorization, CSRF, validation, rate limiting and servlet error dispatches, use this shape:

```json
{
  "timestamp": "2026-10-31T18:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/users",
  "requestId": "8e602015-ea22-4a62-bc33-cda40345b09d",
  "details": [],
  "violations": [
    {"field": "email", "code": "INVALID_EMAIL", "params": {}},
    {"field": "username", "code": "INVALID_LENGTH", "params": {"min": 0, "max": 100}}
  ],
  "params": {}
}
```

All envelope fields are present. `details` and `violations` are arrays, including when empty; `params` is an object. Timestamps are UTC ISO 8601 instants. HTTP `status` is numeric; `error` is the standard HTTP reason phrase. `code` is the behavior identifier. English messages may improve without changing the code.

`message` and `details` contain controlled fallback explanations. Raw exception messages, SQL, stack traces, rejected field values, passwords, account tokens, credentials and upstream provider response bodies are not copied into them. Parameter objects carry explicit metadata such as limits, schema types, environment types and counts. They never include rejected values.

Actuator health endpoints keep their health response format. Security still denies unrecognized routes, so an unknown URL is not guaranteed to return 404. Errors originating outside this application, such as a reverse proxy response, are outside this envelope.

## Authentication and authorization

| HTTP | Code | Meaning |
|---|---|---|
| 401 | `AUTH_REQUIRED` | Authentication is required |
| 401 | `INVALID_CREDENTIALS` | Login failed; account existence is not disclosed |
| 401 | `SESSION_INVALID` | A stored session was invalidated by an account or session-version change |
| 403 | `ACCESS_DENIED` | The role does not permit the operation |
| 403 | `CREDENTIAL_PERMISSION_REQUIRED` | A separate credential permission is missing |
| 403 | `CSRF_MISSING` | The CSRF token is missing |
| 403 | `CSRF_INVALID` | The supplied CSRF token is invalid |
| 503 | `ACCOUNT_VERIFICATION_UNAVAILABLE` | Current account access could not be verified |

Public password-reset requests retain their generic 202 success response. More detailed error codes do not disclose whether an email address has an eligible account.

## Form and rule violations

DTO and request-parameter validation uses HTTP 400 with `code=VALIDATION_FAILED`. All detected validation errors are returned. Each violation has:

| Field | Contract |
|---|---|
| `field` | Request field path, or `null` for an object or cross-field rule |
| `code` | Stable constraint or business-rule code |
| `params` | Safe interpolation parameters; an empty object when none are needed |

Object fields use paths such as `email` or `mappings[0].sourceFieldPath`. Root request arrays use paths such as `[0].timeoutMs`. Query/path parameters use their API parameter names. Constraint validation on service methods can include the method parameter path. A code can apply to more than one field; process the whole list.

| Constraint code | Parameters |
|---|---|
| `REQUIRED` | None |
| `INVALID_EMAIL` | None |
| `INVALID_LENGTH` | `min`, `max` |
| `INVALID_FORMAT` | None; regular expressions are not exposed |
| `MIN_VALUE` / `MAX_VALUE` | `min` / `max` |
| `POSITIVE_REQUIRED` / `NON_NEGATIVE_REQUIRED` | None |
| `INVALID_MIDDLEWARE_SELECTION` / `INVALID_VALUE` | None |

Business errors such as `CURRENT_PASSWORD_INVALID`, `PASSWORD_REUSE`, `USERNAME_TAKEN` and `EMAIL_UNAVAILABLE` also identify their relevant form field in `violations`.

Completion uses HTTP 409 with `code=INTEGRATION_INCOMPLETE`. Its individual requirements are code-bearing violations, usually with `field=null` and parameters such as `environment`, `schemaType` or `mappingId`. The legacy `details: string[]` remains as safe English fallback text. Clients should localize the violations, rather than parse those strings.

```json
{
  "code": "INTEGRATION_INCOMPLETE",
  "violations": [
    {"field": null, "code": "DESTINATION_CONFIG_REQUIRED", "params": {"environment": "DEV"}},
    {"field": null, "code": "REQUIRED_SCHEMA_MISSING", "params": {"schemaType": "SOURCE_TO_DEST_REQUEST"}}
  ]
}
```

The preceding snippet shows only the relevant fields; actual responses include the full envelope.

## Request correlation

Every HTTP request receives a server-generated UUID in `X-Request-ID`. HTTP error bodies repeat the same value as `requestId`. Client-supplied IDs are not trusted or echoed. The identifier is available in the request's logging context and in the common API error log entry. Logging context is restored when the request ends.

A job-status polling request has its own request ID. The durable AI operation is identified by `jobId`; worker failures are tracked by that job ID. A failed job is not the same event as a failed polling request.

## Rate limiting

Every application-generated HTTP 429 includes `Retry-After` as a positive integer delay in seconds:

| Code | Delay |
|---|---|
| `RATE_LIMITED` | Remaining IP rate-limit window, rounded up |
| `INVITATION_RATE_LIMITED` | Remaining account invitation cooldown, rounded up |
| `PASSWORD_RESET_RATE_LIMITED` | Remaining account reset cooldown, rounded up |
| `EMAIL_CHANGE_RATE_LIMITED` | Remaining account email-change cooldown, rounded up |
| `AI_QUEUE_FULL` | 5 seconds; advisory queue retry delay |

A generic framework 429 falls back to 60 seconds. The delay does not guarantee that the next request succeeds and does not trigger an automatic backend retry. `pollAfterMs` is a separate job polling interval.

## Asynchronous AI and capability codes

The same catalog distinguishes three locations:

| Location | Meaning |
|---|---|
| HTTP error envelope `code` | The current API request failed |
| Job representation `errorCode` | The asynchronous operation failed or expired; the GET can still return HTTP 200 |
| Capability representation `code` | Provider availability; `READY` is a success code and `available=false` indicates an unavailable capability |

An upstream provider 429 becomes job `errorCode=AI_RATE_LIMITED`. It is not an HTTP 429 from the job-status endpoint and does not require that successful GET to carry `Retry-After`. Provider authentication failures are not user-session authentication failures.

Gemini safety or content-policy blocks become job `status=FAILED` with `errorCode=AI_CONTENT_BLOCKED`. The job-status GET still returns HTTP 200. The English fallback is "AI provider blocked suggestion generation for this content"; raw provider explanations are not returned. This code is a generation failure, not a capability availability code.

```json
{
  "jobId": "example-job-id",
  "status": "FAILED",
  "createdAt": "2026-10-31T18:00:00Z",
  "expiresAt": "2026-11-01T18:00:00Z",
  "errorCode": "AI_RATE_LIMITED",
  "message": "AI provider quota or rate limit was reached",
  "resultUrl": null,
  "pollAfterMs": null
}
```

Expired jobs expose `status=EXPIRED` and `errorCode=AI_RESULT_EXPIRED`. Fetching or appending an expired result returns a regular HTTP 410 error envelope.

Mapping diagnostics (`AMBIGUOUS`, `NO_COMPATIBLE_FIELD`, `TYPE_MISMATCH`, `INSUFFICIENT_CONTEXT`, `TRANSFORMATION_REQUIRED`, `NO_CONFIDENT_MATCH`) describe unmatched fields; they are not failed-job codes. Fixed diagnostics and warnings are English. The AI prompt requests English suggestion reasons.

## OpenAPI and maintenance

ADMIN sessions can access `/v3/api-docs` and `/swagger-ui/index.html`. OpenAPI includes shared `ErrorResponse` and `Violation` schemas, the complete error-code enum, common error responses and rate-limit response headers. Successful endpoint schemas are preserved.

The runtime code and English message definitions are in `exception/ErrorCode.java`. Endpoint/context metadata is maintained in `scripts/generate_error_catalog.py`; generated catalog files must be committed with code changes:

```sh
python3 scripts/generate_error_catalog.py
python3 scripts/generate_error_catalog.py --check
```

CI checks the generated files. Contract tests cover security, DTO/query/array validation, framework errors, sanitization, correlation headers, rate limits, completion requirements, AI job representations and OpenAPI schemas.
