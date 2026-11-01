# Backend documentation

The [project README](../README.md) is the starting point for quick setup, service
addresses, and test commands. These guides cover separate topics. Link to the
relevant guide instead of repeating the same API rules in several files.
Update the related documents whenever the code or API changes.

| Guide | Content |
|---|---|
| [Configuration and operations](configuration.md) | Docker, environment changes, IDE setup, health, volumes, and troubleshooting |
| [Database migrations](database-migrations.md) | Flyway startup, initial schema, local database reset, and adding migrations |
| [Error contract and catalog](error-codes.md) | English error responses, code/endpoint index, field errors, request IDs, 429, and AI job errors |
| [API reference](api-reference.md) | All controller endpoints, responses, enums, and validation rules |
| [Frontend integration](frontend-integration.md) | Separate frontend/proxy, session/CSRF helper, screen workflows, and acceptance checks |
| [Shared system catalog](systems-frontend.md) | System management, active status, usage, integration choices, map, and environment filter |
| [Integration workflow](integration-workflow.md) | Creation, settings, required schemas, DRAFT/COMPLETED, and review |
| [Payloads and sample files](payload-content.md) | Formats, independent targets, upload/download, field paths, and database tests |
| [AI mapping](ai-mapping.md) | Ollama/Gemini setup, asynchronous suggestion API, and user approval |
| [Authentication and credential security](authentication.md) | Roles, sessions, secret permissions, encryption, and legacy data conversion |
| [User lifecycle](user-management.md) | Invitations, resets, email verification, SMTP, and the outbox |
| [Architecture and validation](architecture.md) | Data relationships, transactions, test coverage, and production limits |

Frontend developers can start with the frontend guide, then the API reference,
then the guide for the feature they need. Use the configuration guide for setup
and operations. Use the architecture guide for scope and release decisions.
This repository does not include a frontend application.

## Where to check the code

- Endpoints and HTTP responses: `src/main/java/com/company/integration_catalog/controller/`.
- Field validation: `dto/` under the same package and request classes in controllers.
- Completion: `validation/IntegrationCompletionValidator.java`.
- Business rules: `service/` and `service/impl/`; access control: `security/` and `config/`.
- Runtime settings: `src/main/resources/application.properties`, `docker-compose.yml`, `.env.example`, `Dockerfile`.
- Versioned schema changes: `src/main/resources/db/migration/`.
- AI contract: `ai/AiMappingDtos.java`, `ai/AiMappingService.java`, and the provider adapters.

IDs in examples are placeholders. Use IDs from your own API responses. Example
secrets and passwords are not production values. Frontend guidance describes how
to use the backend API; it does not mean those screens already exist.
