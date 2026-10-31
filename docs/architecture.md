# Architecture, scope, and validation

The backend is a single Spring Boot application. Controllers handle HTTP and DTOs.
Services handle business rules and transactions. Repositories provide JPA/PostgreSQL
access. Security filters check sessions, CSRF, roles, and current account access.

## Data relationships

The `CatalogSystem` entity is stored in `systems` and has no fixed role. An
integration must reference a source and target from this table; middleware is
optional. Environment settings, schemas, and field mappings belong to the
integration. Deleting a shared system does not clear integrations or their foreign
keys. Deletion is blocked while the system is in use.
`usageCount` counts draft and completed integrations across all three roles. Each
integration is counted once. Shared system rows are locked in increasing ID order,
regardless of their role.

A user account can have several token records. Accounts are stored in
`user_accounts`, extra permissions in `user_permissions`, single-use tokens in
`account_tokens`, account audit records in `account_audit`, and encrypted email
jobs in `mail_outbox`. Use the model classes as the source for physical table
and column details generated from entity names.

## Consistency and security

Technical changes, related data changes, and completion run in transactions.
The integration and selected catalog rows are locked where required. Catalog
activation/deletion checks and new system assignments use the same row locks.
Protecting the last active ADMIN and consuming tokens also use transactions and
row locks. Actual PostgreSQL behavior needs separate validation.

Integrations use entity versioning. However, existing integration PUT and full-list
mapping POST requests do not accept an expected version from the client. These
legacy flows do not fully prevent an old form from overwriting another user's
changes. The newer AI context/job/append flow rejects old saves through
`contextRevision`, calculated from schemas and current mappings.

Adding or removing middleware keeps related data. The first successful sample save
for the new flow removes incompatible old schemas and their mappings. Mappings are
deleted before schemas. Cleanup and the new save share one transaction; failure
keeps the old data. See the [payload contract](payload-content.md#formats-and-flow-model).

Passwords use PBKDF2 hashes. External system credentials use reversible AES-GCM
encryption. The token validation table stores SHA-256 hashes. An encrypted copy of
the raw link is kept temporarily in the outbox so the email can be sent. Roles and
credential permissions are separate. API error responses do not contain raw
exceptions or SQL. `requestId` / `X-Request-ID` connects errors to log entries.
Every protected request compares its session version with the current account.
Details: [security](authentication.md), [user lifecycle](user-management.md).

## Email workflow

A 202/201 response does not wait for SMTP delivery. If a worker stops after SMTP
accepts a message but before the transaction commits, the message may be sent
twice. The token is still single-use. A long queue delay may cause the token to
expire before the email arrives. `mail-status` reports pending/failed counts;
it does not measure inbox delivery. SMTP failure does not make the catalog unready.

## Release and production limits

The backend is for local use and demo/MVP work. Production acceptance is not
complete. [CI and repository checks](../README.md#repository-checks-and-ci) help
validate local source files. Check remote Actions results separately.

- There is no integration execution or external system connection test. Optional
  [AI field suggestions](ai-mapping.md) support JSON/XML/IDOC pairs and require user
  approval. The backend selects `ollama` or `gemini`. Both adapters share field
  validation and append rules. Gemini sends field profiles and optional sample
  values to Google. There is no automatic provider fallback.
- The full JSON Schema standard and runtime type conversions are not validated.
  The AI/append flow checks JSON/XML/IDOC field paths. Legacy manual JSON records
  with a single definition have limited path checks. CSV/Excel are sample files only.
- There is no single-schema delete endpoint.
- Credential maps have no required-key contract for each authentication type.
- Revealing secrets has no password recheck, MFA, or persistent database audit endpoint.
- Sessions and authentication rate limits are held in instance memory. Shared
  session storage and distributed rate limits are not configured.
- Catalog/integration lists have no pagination. Performance with large data needs testing.
- The project uses `ddl-auto=update`. Versioned Flyway/Liquibase migrations are not in place.
- API errors use a shared English response format with coded field `violations`.
  Actuator health responses keep their own format. The [error contract](error-codes.md)
  and catalog support frontend localization.
- The frontend is not included. Account pages and a production reverse proxy need
  separate setup.
- Compose is for localhost/HTTP development. Production needs HTTPS, Secure cookies,
  a restricted database user, monitoring/log management, and backup recovery with
  the same encryption key. An `unhealthy` container is not restarted automatically.
- Dependency/secret scans, review of general exception logs, and PostgreSQL load/lock
  tests with concurrent requests are separate production acceptance tasks.
- Define which samples may be sent to Gemini and set usage/quota limits. Synthetic
  model tests do not guarantee correct mappings for real company data.

## Test coverage and validation methods

| Group | What it checks |
|---|---|
| IntegrationServiceImpl / MappingServiceImpl tests | Updates, schema ownership, and full-list saves |
| PayloadSchemas / PayloadWeb tests | XML/IDOC field trees, CSV/Excel files, size/format/access checks, and mapping restrictions |
| PayloadPostgresTests (opt-in) | Real PostgreSQL JSONB/TEXT/BYTEA save/read and create/update behavior; [run instructions](payload-content.md#database-upgrades-and-tests) |
| SystemCatalogPostgresTests (opt-in) | Shared IDs, unique usage across three roles, system names, environment types, inactive choices, and absence of old tables |
| MappingFlowPostgresTests (opt-in) | Cleanup during flow changes in both directions using text/files, keeping other integrations safe, and rollback after cleanup failures |
| IntegrationCompletion / IntegrationSystemSelection | Required schemas, review, return to draft, and inactive selections |
| SystemManagement | Updates, usage counts, active status, and deletion |
| AiMappingService / AiMappingWorker / AiMappingWeb | Backend provider selection, queue, protected connections, approval/idempotency, roles/CSRF, errors, and provider changes |
| MappingSuggestionProtocol / OllamaProvider / GeminiProvider / AiProviderHttpClient | Nine format pairs, short field IDs, shared type/array rules, and HTTP error/timeout/response limits; external HTTP is mocked |
| AiMappingPostgresTests (opt-in) | Persistent jobs/JSONB results, approval records, nine format pairs, old-context rejection, expiry, and related AI/mapping cleanup |
| Security / CredentialSecurity / CatalogManagementWeb | Real Spring Security filters, roles, CSRF, and secret permissions |
| CredentialCrypto / CredentialMigration | Encryption, wrong keys, damaged data, and conversion with mocked JDBC |
| AccountLifecycle / AccountLifecycleWeb / AccountMail | Invitations, resets, verification, session invalidation, and outbox |
| AdminBootstrap / AuthRateLimitFilter | First user and rate limiting |
| RequestValidation / HealthEndpoint | DTO validation, Actuator privacy, and simulated health states |

`PayloadPostgresTests` checks JSONB/TEXT/BYTEA save/read and create/update behavior
for five formats on real PostgreSQL. Test transactions roll back.
`MappingFlowPostgresTests` checks commits and rollback after cleanup errors in
separate transactions and removes its own test records. Enable these classes,
`SystemCatalogPostgresTests`, and `AiMappingPostgresTests` with
`catalog.payload.postgres-test=true`.

These tests do not cover concurrent account/catalog locks, deleting every related
data type together, schema upgrades from old releases, or real SMTP delivery.
A separate database test checks that deleting an integration removes its related
AI/mapping records. Credential conversion tests mock JDBC calls. HTTP security
tests use real filters. `RequestValidationTests` disables security filters to test
DTOs/controllers only.
`IntegrationCatalogApplicationTests` loads the full application and requires a
working database and configuration. The normal command is in the [project README](../README.md);
optional database commands are in the [payload guide](payload-content.md#database-upgrades-and-tests).
Live Ollama/Gemini checks are separate from automated tests. The [AI validation notes](ai-mapping.md#validation)
describe their samples and limits.

## Access and file processing limits

Role-based catalog access applies to the whole application. There is no separate
ownership, team/tenant isolation, or sharing permission for each integration.
VIEWER/EDITOR users can perform the actions allowed by their role on all catalog
records. This model alone cannot keep integrations private between teams.

CSV/Excel samples and raw XML/IDOC content are not covered by credential encryption.
Files are parsed in the JVM handling the HTTP request, and parsed again during
completion. There is no separate parser process or CPU/time limit for parsing.
The compressed upload size of an Excel file does not limit its memory use after
opening. Before production, review file processing load, sample data masking,
and retention. The library's [file processing security guide](https://poi.apache.org/security.html)
provides more details.
