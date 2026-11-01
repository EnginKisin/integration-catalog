# Configuration and operations

The main files are `application.properties`, `.env.example`, `docker-compose.yml`,
and `Dockerfile`. Compose reads `.env`; IDEs and `./mvnw` do not load it automatically.
Set environment variables separately in your IDE Run Configuration.

## Running with Docker

Compose runs PostgreSQL and the backend on the same network. The backend connects
to `postgres:5432`. See the [project README](../README.md#getting-started) for first setup
variables and quick start. The first image build downloads dependencies and base
images. The multi-stage Java 21 build creates a runtime image with a JRE/JAR.
The application runs as a non-root user. Image builds do not run tests; testing/CI
is a separate step.

```sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

Add `--profile mail` to view invitation/reset emails locally. Mailpit is at
`http://localhost:8025` and does not send to real recipients. See [user management](user-management.md#tokens-email-queue-and-smtp)
for real SMTP setup. Ollama starts only with the `ai` profile. Online model
downloads and the offline runtime use separate networks. Gemini does not need
Ollama. See the [AI guide](ai-mapping.md) for both providers.

Default host addresses are `127.0.0.1:8080` for the backend and `127.0.0.1:5432`
for the database. Change them through `APP_PORT` and `POSTGRES_PORT` in `.env`.
An IDE backend and the container cannot use the same host port at the same time.

### Applying code and environment changes

If only `.env` changed:

```sh
docker compose up -d --wait app
```

If Java source or the Dockerfile changed:

```sh
docker compose up -d --build --wait --wait-timeout 180 app
```

Compose recreates the container with the updated settings. `docker compose restart app`
only restarts the existing container; it does not load new environment values or
build code. Sessions are stored in PostgreSQL; unexpired sessions can survive a
backend restart with compatible application versions and the same browser cookie.
Each request gets `X-Request-ID`; the error body repeats it as `requestId`.
Use this ID to find common error logs. Track AI worker events by `jobId`.
If the database is already ready, add `--no-deps` to update only the app.

`.env` is excluded from Git and the build context. Secrets are passed to the
backend at runtime, not stored in the image. People with Docker administration
access can read the environment. Changing `.env` does not change an existing
database password. Replacing the credential key with a random new key makes old
data unreadable.

### Volumes and existing databases

`postgres_data` stores database data; `ollama_data` stores downloaded models.
Keep the same Compose project name/directory to access the same data.
`docker compose down` keeps volumes. `down -v` deletes them and is not the normal
way to stop services.

This release has no migration from the old separate source/target/middleware
catalogs to shared `systems`. You can start with an empty database. An old database
with saved data needs a separate migration. New databases get their schema from
Flyway migrations. The configuration section below explains the initial transition.

## Environment variables

| Variable | Default / requirement | Effect |
|---|---|---|
| `POSTGRES_USER` | Compose: `catalog_user` | Initial PostgreSQL user and app datasource user |
| `POSTGRES_DB` | Compose: `integration_catalog` | Database name |
| `POSTGRES_PASSWORD` | Required by Compose | Initial database password; does not change an existing volume's password |
| `POSTGRES_PORT` | Compose: `5432` | Host port; always 5432 inside the container |
| `APP_PORT` | Compose: `8080` | Host port; always 8080 inside the container |
| `CATALOG_ADMIN_USERNAME` | Required when the user table is empty | First ADMIN username |
| `CATALOG_ADMIN_PASSWORD` | Required when the user table is empty | First ADMIN password; 12–128 characters |
| `CATALOG_CREDENTIALS_KEY` | Required at every startup | Base64-encoded 32-byte AES key |
| `CATALOG_CREDENTIALS_MIGRATE` | `false` | Encrypt legacy plaintext credentials once |
| `SESSION_COOKIE_SECURE` | `false` | Set to `true` for HTTPS |
| `SMTP_HOST` | IDE: `localhost`; Compose: `mailpit` | SMTP server |
| `SMTP_PORT` | `1025` | SMTP port |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Empty | Real SMTP provider credentials |
| `SMTP_AUTH` | `false` | SMTP authentication |
| `SMTP_STARTTLS` | `false` | When true, STARTTLS is enabled and required |
| `MAIL_FROM` | `no-reply@localhost` | Sender address |
| `ACCOUNT_ACTION_URL` | `http://localhost:5173/account/action` | Frontend account link; HTTPS required outside localhost |

`POSTGRES_*` and `APP_PORT` are Compose variables. They do not directly change
Spring settings in your IDE. Use `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and optionally
`SERVER_PORT` there. Local datasource defaults in the source are
`jdbc:postgresql://localhost:5432/integration_catalog` and `catalog_user`.
There is no default database password. Supply `SPRING_DATASOURCE_PASSWORD`
externally. Compose sets it from `POSTGRES_PASSWORD` in `.env`; set it separately
in the IDE.

`application.properties` is the single source for backend AI defaults. Compose
passes AI variables defined in `.env` or the shell. Undefined variables are not
added to the container environment, so Spring uses its defaults. To return to a
default, remove the optional AI variable's line instead of setting an empty value.
An empty `GEMINI_API_KEY` means no key is used. `AiProperties` only binds and
validates the values. Container defaults such as Ollama image/RAM/CPU stay in Compose.

Spring settings can be overridden through the environment. For example,
`SPRING_JPA_SHOW_SQL=false` disables SQL logging. Flyway manages schema changes
using versioned SQL files in `src/main/resources/db/migration`; Hibernate uses
`ddl-auto=validate` to check entity compatibility after migrations. Start this
transition with an empty database. Existing Hibernate-managed databases must be
reset; see [database migrations](database-migrations.md) for local reset commands
and the process for adding new migrations, including enum CHECK constraints.
`CATALOG_CREDENTIALS_MIGRATE` encrypts old credential content only. It does not
manage table or column migrations.

Spring Session JDBC shares this datasource and stores sessions in `SPRING_SESSION`
and `SPRING_SESSION_ATTRIBUTES`, created by Flyway V2. Schema initialization by
Spring Session is disabled. The existing 30-minute servlet idle timeout and
`JSESSIONID` cookie settings also apply to JDBC sessions. No Redis or additional
environment variable is required. See [shared JDBC sessions](authentication.md#shared-jdbc-sessions)
for restarts, independent installations, cleanup, and deployment compatibility.

## Running from an IDE

1. Select JDK 21. Fill in `.env` as described in [getting started](../README.md#getting-started).
2. Start only the database and local email service:

```sh
docker compose --profile mail up -d postgres mailpit
```

Compose checks required variables even when the app service is not started.
The database password and key must therefore be set in `.env`.

3. In the IDE environment, set datasource values (`SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), the persistent
   credential key, first-start admin values, `SMTP_HOST=localhost`, and
   `SMTP_PORT=1025`. Use host `localhost` and the `.env` POSTGRES_PORT in the database URL.
4. Run `IntegrationCatalogApplication`, or use this command with the same environment:

```sh
./mvnw spring-boot:run
```

The IDE and Compose app cannot use the same host port. The first admin has no
email. After login, link and verify an address through the email-change flow.
Forgot-password recovery needs a verified email. Bootstrap settings cannot
overwrite existing accounts.

## Timeouts and intervals

| Setting | Current value / meaning |
|---|---|
| Session idle timeout | 30 minutes |
| Config `timeoutMs`, `retryCount`, `retryIntervalMs` | User metadata; does not send or retry external requests |
| General HTTP processing time | No shared application request deadline is configured |
| Payload text | Maximum 1 MiB; JSON/XML/IDOC/CSV |
| Excel file | Maximum 5 MiB; total multipart request limit 6 MiB |
| XML parsing | 64 levels, 10000 fields, 500 characters per field path |
| Hikari connection timeout | Compose app: 5000 ms; time to wait for a pool connection |
| Hikari validation timeout | Compose app: 2000 ms; connection validation limit |
| SMTP connection / read / write | 5000 ms each |
| Email worker | Every 15 seconds, up to 10 jobs; at most 8 attempts |
| Auth POST IP limit | 20 requests per 60 seconds for login/invitation acceptance/password/email actions; stored in instance memory |
| Token creation for one account/purpose | At least 60 seconds apart |
| Invitation lifetime | 24 hours |
| Reset / email verification | 30 minutes |
| Docker app health | Every 15 seconds, 60-second startup allowance, 3 failed checks |
| Compose stop grace | 30 seconds |
| AI model request | Default 300 seconds, excluding queue time; maximum 600 |
| AI model output | Default 2048 tokens; truncated output is rejected |

AI jobs are asynchronous. The frontend polls at `pollAfterMs` intervals. The model
timeout is not how long the initiating HTTP request stays open. See [AI mapping](ai-mapping.md)
for local model performance and Ollama/Gemini setup. `AI_PROVIDER`/`AI_MODEL` are
backend settings. Gemini needs `GEMINI_API_KEY`. Compose passes this key only to
the app service, not to Ollama.

Hikari limits are not HTTP/SQL query completion deadlines. Compose values are not
passed to an IDE automatically. Docker health uses an 8-second curl limit and a
10-second Docker check timeout.

## Health checks

Spring Boot Actuator exposes only health endpoints:

| Endpoint | Check | Purpose |
|---|---|---|
| `GET /actuator/health` | Overall health | Monitoring |
| `GET /actuator/health/liveness` | Application liveness | Restart decisions |
| `GET /actuator/health/readiness` | Readiness and PostgreSQL connection | Traffic routing |

All three are public. Their responses hide components, connection addresses, and
error details from every user, including ADMIN. Other Actuator paths are denied.
Healthy responses have `status=UP` and HTTP 200. DOWN or OUT_OF_SERVICE returns 503.

Losing PostgreSQL makes readiness fail but does not affect liveness. Traffic can
be stopped without repeatedly restarting the app for a database problem.
Readiness also checks Spring Boot's startup/shutdown state. The app is not ready
until startup work, such as credential conversion, has finished.

Local check:

```sh
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

These endpoints do not prove that every database record is valid or that external
integrations work. Docker Compose checks app readiness through the Dockerfile
HEALTHCHECK. See [Docker setup](#running-with-docker) for startup and limits.
SMTP health is off; ADMIN monitors the email queue separately through
`GET /api/v1/users/mail-status`. Health checks do not test the full
access-control/CSRF workflow.

`HealthEndpointTests` uses real Actuator and security filters to check endpoint
access, privacy, availability states, and a simulated database outage. It does
not disconnect a real PostgreSQL database.

AI providers are also excluded from overall readiness. An AI failure does not
block manual mapping. Query `GET /api/v1/integrations/{id}/mappings/ai/capabilities`
separately.

The app starts after PostgreSQL `pg_isready` succeeds. Docker HEALTHCHECK calls
readiness every 15 seconds. After a 60-second startup allowance and three failed
checks, the container becomes `unhealthy`. `restart: unless-stopped` restarts an
exited process. It does not restart a manually stopped service or a running
process that is only `unhealthy`. Compose does not stop traffic based on health.

## Backups and key management

Keep the database backup and encryption key in separate safe places. Without the
key, credentials and encrypted emails cannot be decrypted. A new random key will
not recover old data. Automatic key rotation is not available. Test recovery in a
test environment with the same key, and check application/schema compatibility.
`docker compose down` keeps the volume. `down -v` deletes data and is not a normal
restart method.

For legacy plaintext credential conversion, stop other app instances, take a
backup, and follow the [encryption conversion steps](authentication.md). Turn the
migration flag off after successful startup. Old backups keep their plaintext values.

## Troubleshooting

| Symptom | Check |
|---|---|
| First startup stops | Database connection, Base64 32-byte key, and bootstrap settings if the user table is empty |
| Key / decryption error | Use the persistent key for this database; a new key cannot decrypt old data |
| Compose database authentication error | Does the environment password match the password used when the volume was created? |
| Login/write returns 403 | Same session cookie and current CSRF header; is Secure cookie enabled by mistake on HTTP? |
| 401 after login | Invitation verification, active state, required reset, or invalidated sessions |
| Request returns 409 | `code`, `violations`, safe parameters, and `requestId` for related error logs |
| Payload returns 400 / 409 / 413 | `code`, format/file type/size, existing mapping conflicts; [examples](payload-content.md#errors) |
| No email | Mail profile / SMTP settings, ADMIN mail-status, app logs, token expiry |
| Container unhealthy | Readiness and database connection; Compose does not restart it for unhealthy status alone |
| Frequent 429 behind a proxy | The IP limit uses the remote address; proxy users may share one visible IP |

```sh
docker compose ps
docker compose logs --tail=100 app
curl -i http://localhost:8080/actuator/health/readiness
```

Remove credentials before sharing logs. HTTP body and SMTP debug logs can expose
passwords/tokens. User management audit is in the database; credential reveal
audit is in application logs. Retention and central logging need separate setup.
