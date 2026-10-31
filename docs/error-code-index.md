# Error code index

This index lists every supported error code. Contexts distinguish top-level HTTP errors, nested violations, asynchronous job states and capability availability.

Endpoint patterns describe route families; the applicable checks depend on the operation and payload. Examples for every code and context are in [the JSON catalog](error-catalog.json). The shared response contract is in [API errors](error-codes.md).

| Code | Context / HTTP | Endpoints | English fallback |
|---|---|---|---|
| `SESSION_INVALID` | http 401 | `* /api/v1/**`<br>`GET /swagger-ui/**`<br>`GET /v3/api-docs/**` | Session is no longer valid; sign in again |
| `AUTH_REQUIRED` | http 401 | `* /api/v1/**`<br>`GET /swagger-ui/**`<br>`GET /v3/api-docs/**` | Authentication is required |
| `INVALID_CREDENTIALS` | http 401 | `POST /api/v1/auth/login` | Invalid username or password |
| `ACCESS_DENIED` | http 403 | `* /api/v1/**`<br>`GET /swagger-ui/**`<br>`GET /v3/api-docs/**` | Access denied |
| `CSRF_MISSING` | http 403 | `POST / PUT / PATCH / DELETE /api/v1/**` | CSRF token is missing |
| `CSRF_INVALID` | http 403 | `POST / PUT / PATCH / DELETE /api/v1/**` | CSRF token is invalid |
| `ACCOUNT_VERIFICATION_UNAVAILABLE` | http 503 | `* /api/v1/**` | Account verification unavailable |
| `RATE_LIMITED` | http 429 | `POST /api/v1/auth/**` | Too many requests; try again later |
| `VALIDATION_FAILED` | http 400 | `* /api/v1/**` | Request validation failed |
| `INVALID_REQUEST_BODY` | http 400 | `* /api/v1/**` | Invalid request body |
| `INVALID_PARAMETER` | http 400 | `* /api/v1/**` | A request parameter has an invalid value |
| `MISSING_REQUEST_HEADER` | http 400 | `* /api/v1/**` | A required request header is missing |
| `MISSING_REQUEST_PARAMETER` | http 400 | `* /api/v1/**` | A required request parameter is missing |
| `MISSING_UPLOAD_PART` | http 400 | `POST /api/v1/integrations/{id}/mappings/schemas/upload` | A required upload part is missing |
| `METHOD_NOT_ALLOWED` | http 405 | `* /api/v1/**` | HTTP method is not supported for this endpoint |
| `UNSUPPORTED_MEDIA_TYPE` | http 415 | `* /api/v1/**` | Request content type is not supported |
| `NOT_ACCEPTABLE` | http 406 | `* /api/v1/**` | The requested response content type is not available |
| `RESOURCE_NOT_FOUND` | http 404 | `* /api/v1/**` | Requested resource was not found |
| `INTEGRATION_NOT_FOUND` | http 404 | `* /api/v1/integrations/{id}`<br>`* /api/v1/integrations/{id}/**` | Integration was not found |
| `SYSTEM_NOT_FOUND` | http 404 | `GET / PUT / DELETE /api/v1/systems/{id}`<br>`PATCH /api/v1/systems/{id}/active`<br>`POST /api/v1/integrations`<br>`PUT /api/v1/integrations/{id}` | System was not found |
| `SCHEMA_NOT_FOUND` | http 404 | `GET /api/v1/integrations/{id}/mappings/schemas/{schemaId}/sample`<br>`POST /api/v1/integrations/{id}/mappings/fields`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Schema was not found in this integration |
| `AI_JOB_NOT_FOUND` | http 404 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}/result`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | AI job was not found in this integration |
| `SAMPLE_FILE_NOT_FOUND` | http 404 | `GET /api/v1/integrations/{id}/mappings/schemas/{schemaId}/sample` | Sample file was not found |
| `ACCOUNT_NOT_FOUND` | http 404 | `GET /api/v1/auth/me`<br>`POST /api/v1/auth/password/change`<br>`POST /api/v1/auth/email/change`<br>`POST / PUT /api/v1/users/{id}/**` | Account was not found |
| `CONFIG_NOT_FOUND` | http 404 | `GET /api/v1/integrations/{id}/configs/{environment}/{target}/credentials` | Environment configuration was not found |
| `CONCURRENT_MODIFICATION` | http 409 | `* /api/v1/**` | The record changed; reload and try again |
| `DATA_CONFLICT` | http 409 | `* /api/v1/**` | Data conflicts with an existing record |
| `INTERNAL_ERROR` | http 500 | `* /api/v1/**` | An unexpected error occurred |
| `SERVICE_UNAVAILABLE` | http 503 | `* /api/v1/**` | Service is temporarily unavailable |
| `BAD_REQUEST` | http 400 | `* /api/v1/**` | Request could not be processed |
| `CONFLICT` | http 409 | `* /api/v1/**` | Request conflicts with the current state |
| `USERNAME_TAKEN` | http 409 | `POST /api/v1/users` | Username already exists |
| `INVITATION_NOT_PENDING` | http 409 | `POST /api/v1/users/{id}/invitations/resend` | Account is not awaiting invitation acceptance |
| `INVITATION_RATE_LIMITED` | http 429 | `POST /api/v1/users/{id}/invitations/resend` | Wait before sending another invitation |
| `ACCOUNT_EMAIL_REQUIRED` | http 409 | `POST /api/v1/users/{id}/password-reset` | Account requires an active verified email |
| `LAST_ADMIN_RESET_RESTRICTED` | http 409 | `POST /api/v1/users/{id}/password-reset` | The last active administrator must use self-service password recovery |
| `PASSWORD_RESET_RATE_LIMITED` | http 429 | `POST /api/v1/users/{id}/password-reset` | Wait before requesting another password reset |
| `EMAIL_UNAVAILABLE` | http 409 | `POST /api/v1/users`<br>`POST /api/v1/auth/email/change` | Email cannot be used |
| `EMAIL_CHANGE_RATE_LIMITED` | http 429 | `POST /api/v1/auth/email/change` | Wait before requesting another email change |
| `LAST_ADMIN_REQUIRED` | http 409 | `PUT /api/v1/users/{id}/role`<br>`PUT /api/v1/users/{id}/active` | The last active administrator must be retained |
| `EMAIL_VERIFICATION_REQUIRED` | http 409 | `PUT /api/v1/users/{id}/active` | Email verification is required before activation |
| `ACCOUNT_UNAVAILABLE` | http 403 | `POST /api/v1/auth/password/change`<br>`POST /api/v1/auth/email/change` | Account is unavailable |
| `CURRENT_PASSWORD_INVALID` | http 400 | `POST /api/v1/auth/password/change`<br>`POST /api/v1/auth/email/change` | Current password is incorrect |
| `PASSWORD_REUSE` | http 400 | `POST /api/v1/auth/password/change`<br>`POST /api/v1/auth/password-reset/confirm` | Choose a different password |
| `ACCOUNT_LINK_INVALID` | http 400 | `POST /api/v1/auth/invitations/accept`<br>`POST /api/v1/auth/password-reset/confirm`<br>`POST /api/v1/auth/email/confirm` | Account link is invalid or expired |
| `CREDENTIAL_PERMISSION_REQUIRED` | http 403 | `POST /api/v1/integrations/{id}/configs`<br>`GET /api/v1/integrations/{id}/configs/{environment}/{target}/credentials` | Credential permission is required |
| `SYSTEM_IN_USE` | http 409 | `DELETE /api/v1/systems/{id}` | System is used by integrations; deactivate it instead |
| `INACTIVE_SYSTEM_SELECTION` | http 409 | `POST /api/v1/integrations`<br>`PUT /api/v1/integrations/{id}` | Inactive systems cannot be selected |
| `DUPLICATE_ENVIRONMENT_CONFIG` | http 400; violation 409 | `POST /api/v1/integrations/{id}/configs`<br>`PATCH /api/v1/integrations/{id}/complete` | Environment configuration is duplicated |
| `CREDENTIALS_EMPTY` | http 400 | `POST /api/v1/integrations/{id}/configs` | Credentials must not be empty |
| `CREDENTIALS_REQUIRED` | http 400 | `POST /api/v1/integrations/{id}/configs` | Credentials are required for this authentication type |
| `INTEGRATION_INCOMPLETE` | http 409 | `PATCH /api/v1/integrations/{id}/complete` | Integration is not ready for completion |
| `REVIEW_REQUIRED` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Review preserved settings and confirm the review before completing |
| `BASIC_INFORMATION_REQUIRED` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Basic integration information is incomplete |
| `INVALID_MIDDLEWARE_SELECTION` | violation 409; violation 400 | `PATCH /api/v1/integrations/{id}/complete`<br>`* /api/v1/**` | Middleware selection is inconsistent |
| `DUPLICATE_SCHEMA_TYPE` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Schema type is duplicated |
| `REQUIRED_SCHEMA_MISSING` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | A required schema is missing |
| `INVALID_MAPPING_CONNECTION` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Field mapping has an invalid schema connection or an empty field path |
| `ENVIRONMENT_CONFIG_REQUIRED` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | At least one environment configuration is required |
| `INVALID_ENVIRONMENT_CONFIG` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Environment configuration is invalid |
| `MIDDLEWARE_CONFIG_NOT_ALLOWED` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Middleware configuration is incompatible with the integration flow |
| `DESTINATION_CONFIG_REQUIRED` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Destination configuration is required for this environment |
| `MIDDLEWARE_CONFIG_REQUIRED` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Middleware configuration is required for this environment |
| `REQUIRED` | violation 400 | `* /api/v1/**` | A value is required |
| `INVALID_EMAIL` | violation 400 | `* /api/v1/**` | Email address is invalid |
| `INVALID_LENGTH` | violation 400 | `* /api/v1/**` | Length is outside the allowed range |
| `INVALID_FORMAT` | violation 400 | `* /api/v1/**` | Value has an invalid format |
| `INVALID_VALUE` | violation 400 | `* /api/v1/**` | Value is invalid |
| `MIN_VALUE` | violation 400 | `* /api/v1/**` | Value is below the minimum |
| `MAX_VALUE` | violation 400 | `* /api/v1/**` | Value exceeds the maximum |
| `POSITIVE_REQUIRED` | violation 400 | `* /api/v1/**` | Value must be greater than zero |
| `NON_NEGATIVE_REQUIRED` | violation 400 | `* /api/v1/**` | Value must be zero or greater |
| `INVALID_SCHEMA_TYPE` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | Schema type is incompatible with the integration flow |
| `FORMAT_MISMATCH` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | Payload format does not match the integration flow |
| `FORMAT_REQUIRED` | http 400 | `POST /api/v1/integrations/{id}/mappings/schemas/upload` | Payload format is required |
| `INVALID_CONTENT` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | Content is incompatible with the selected format or flow |
| `INVALID_FILE` | http 400 | `POST /api/v1/integrations/{id}/mappings/schemas/upload` | Uploaded file could not be read |
| `INVALID_JSON` | http 400 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload` | JSON content must be a valid object |
| `FILE_REQUIRED` | violation 409; http 400 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload` | A sample file is required |
| `MAPPING_NOT_SUPPORTED` | violation 409; http 400; http 400 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/fields` | Selected format does not support field mapping |
| `SAMPLE_REQUIRED` | violation 409; http 422 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/fields`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | An independent definition or sample is required |
| `INVALID_SCHEMA` | http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | Stored schema does not match its content |
| `EMPTY_SCHEMA` | violation 409 | `PATCH /api/v1/integrations/{id}/complete` | Schema definition must not be empty |
| `FIELD_PATH_NOT_FOUND` | violation 409; http 400; http 422 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/fields`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Field path does not exist in the selected schema |
| `INVALID_ENCODING` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | Only UTF-8 text is supported |
| `INVALID_XML` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | XML content is malformed or unsupported |
| `XML_COMPLEXITY_LIMIT` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | XML exceeds supported depth, field count or path length |
| `INVALID_IDOC` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | IDOC XML must contain an IDOC element |
| `INVALID_CSV_OPTIONS` | violation 409; http 400 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload` | CSV options are invalid |
| `INVALID_CSV` | violation 409; http 400 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload` | CSV content is malformed or inconsistent |
| `INVALID_EXCEL` | violation 409; http 400 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload` | A readable, unencrypted workbook matching its extension is required |
| `EMPTY_CONTENT` | http 400; http 422; violation 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`PATCH /api/v1/integrations/{id}/complete` | Content must not be empty |
| `PAYLOAD_TOO_LARGE` | violation 409; http 413; http 422 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Content exceeds the allowed size |
| `INVALID_FILE_NAME` | http 400 | `POST /api/v1/integrations/{id}/mappings/schemas/upload` | File name is invalid |
| `UNSUPPORTED_FILE_EXTENSION` | http 400 | `POST /api/v1/integrations/{id}/mappings/schemas/upload` | File extension does not match the selected format |
| `INVALID_JSON_SCHEMA` | violation 409; http 422 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/fields`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | JSON schema properties must have object definitions |
| `INPUT_TOO_COMPLEX` | violation 409; http 422 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/fields`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Input exceeds supported nesting or path length |
| `TOO_MANY_FIELDS` | violation 409; http 422 | `PATCH /api/v1/integrations/{id}/complete`<br>`POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload`<br>`POST /api/v1/integrations/{id}/mappings/fields`<br>`POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Input exceeds the allowed field count |
| `SCHEMA_MAPPING_CONFLICT` | http 409 | `POST /api/v1/integrations/{id}/mappings/schemas`<br>`POST /api/v1/integrations/{id}/mappings/schemas/upload` | Schema change would invalidate existing mappings |
| `INVALID_IDEMPOTENCY_KEY` | http 400 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | A valid Idempotency-Key header is required |
| `INVALID_SCHEMA_PAIR` | http 422 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Schema pair is incompatible with the integration flow |
| `AI_INPUT_TOO_LARGE` | http 413 | `POST /api/v1/integrations/{id}/mappings/ai/jobs` | AI input exceeds the allowed size |
| `AI_CONTEXT_LIMIT` | http 413 | `POST /api/v1/integrations/{id}/mappings/ai/jobs` | AI input exceeds the context budget |
| `AI_QUEUE_FULL` | http 429 | `POST /api/v1/integrations/{id}/mappings/ai/jobs` | AI queue is full; try again later |
| `CONTEXT_CHANGED` | http 409 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Schemas or mappings changed; reload the mapping context |
| `MAPPING_CONFLICT` | http 409 | `POST /api/v1/integrations/{id}/mappings/fields/append` | A target field already has a mapping |
| `INVALID_SUGGESTION` | http 422 | `POST /api/v1/integrations/{id}/mappings/fields/append` | Suggestion is invalid for this job or schema pair |
| `AI_DISABLED` | http 503; capability 200 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI suggestions are disabled; manual mapping is available |
| `AI_PROVIDER_NOT_SUPPORTED` | http 503; capability 200 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | Configured AI provider is not supported |
| `AI_FORMAT_NOT_SUPPORTED` | http 422 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | AI mapping supports JSON, XML and IDOC |
| `AI_RESULT_EXPIRED` | http 410; job 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}/result`<br>`POST /api/v1/integrations/{id}/mappings/fields/append`<br>`GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}` | AI result expired; request new suggestions |
| `AI_RESULT_NOT_READY` | http 409 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}/result`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | AI result is not available; inspect the job status |
| `IDEMPOTENCY_CONFLICT` | http 409 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`POST /api/v1/integrations/{id}/mappings/fields/append` | Idempotency key was used for a different request |
| `AI_API_KEY_MISSING` | http 503; job 200; capability 200 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI provider API key is not configured |
| `AI_CONFIGURATION_INVALID` | http 503; job 200; capability 200 | `POST /api/v1/integrations/{id}/mappings/ai/jobs`<br>`GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI provider configuration is invalid |
| `AI_AUTHENTICATION_FAILED` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI provider credentials or permissions were rejected |
| `AI_MODEL_NOT_AVAILABLE` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | Configured AI model is not available |
| `AI_RATE_LIMITED` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI provider quota or rate limit was reached |
| `AI_TIMEOUT` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI request timed out; manual mapping is available |
| `AI_UNAVAILABLE` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI provider is temporarily unavailable |
| `AI_INVALID_OUTPUT` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | AI provider returned an invalid mapping result |
| `AI_CONTENT_BLOCKED` | job 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}` | AI provider blocked suggestion generation for this content |
| `AI_OUTPUT_TRUNCATED` | job 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}` | AI output exceeded its limit |
| `AI_INTERRUPTED` | job 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}` | AI execution was interrupted |
| `AI_CONFIGURATION_CHANGED` | job 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}` | AI provider configuration changed; create a new job |
| `AI_INTERNAL_ERROR` | job 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}` | AI suggestion generation failed; manual mapping is available |
| `AI_MODEL_NOT_INSTALLED` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | Configured local AI model is not installed |
| `AI_CLOUD_MODEL_FORBIDDEN` | job 200; capability 200 | `GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}`<br>`GET /api/v1/integrations/{id}/mappings/ai/capabilities` | Only downloaded local AI models are supported |
