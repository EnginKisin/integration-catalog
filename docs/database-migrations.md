# Database migrations

Flyway manages the PostgreSQL schema in every environment, including local
development and database tests. Hibernate uses `ddl-auto=validate`: it checks
entity compatibility but does not create or alter tables. This is a backend
change; API contracts and frontend code are unaffected.

## Startup and first installation

Spring Boot runs the versioned SQL files in
`src/main/resources/db/migration` before creating the JPA EntityManagerFactory.
`V1__initial_schema.sql` creates the complete initial schema, including keys,
indexes, enum CHECK constraints, and JSONB/TEXT/BYTEA columns. Flyway records
applied migrations and their checksums in `flyway_schema_history`. On later
starts, it validates that history and applies only pending migrations.

`V2__create_session_tables.sql` adds the Spring Session JDBC tables. A database
already managed by Flyway at V1 applies V2 automatically without resetting data.
Only the original transition from an unmanaged Hibernate schema requires the
empty-database setup below. Session schema initialization outside Flyway is
disabled; see [session storage](authentication.md#shared-jdbc-sessions).

Start with an **empty database**. The first administrator is created afterward
using the bootstrap settings described in [authentication](authentication.md#first-administrator).
An existing database previously managed by Hibernate must be reset for this
transition. There is no legacy baseline or data-preservation migration, and
automatic baselining is not enabled. Do not enable `baseline-on-migrate` to bypass
the initial migration: that would skip creating and checking the intended schema.

To reset only the local Compose database, the following commands **permanently
delete all catalog data**. AI model storage is unaffected. Run these from the
repository root with `.env` configured and Docker running:

```sh
docker compose stop app
docker compose up -d --wait postgres
docker compose exec -T postgres sh -ec 'psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'
docker compose --profile mail up --build -d --wait --wait-timeout 180
```

Stop any IDE instance before resetting. Bootstrap admin credentials must be set
again because the reset removes all users. A new installation with an empty
database needs only the normal [getting started](../README.md#getting-started)
commands. For a PostgreSQL instance managed outside Compose, recreate the
application database/schema using that instance's administration tools.

## Changing the schema

1. Add a new SQL file, for example `V3__add_customer_reference.sql`, under
   `src/main/resources/db/migration`. Use the next unused version and two
   underscores between the version and description.
2. Include the required DDL and any data updates. For example, populate existing
   rows before adding a NOT NULL constraint. Enum changes also require updating
   the corresponding CHECK constraints explicitly.
3. Update the entity mappings and run the PostgreSQL tests on a fresh test
   database. For later releases, also test upgrading a database at the previous
   migration version with representative data.
4. Commit the migration together with the backend change. Deploying the backend
   applies pending migrations automatically.

Never edit or rename an applied migration. Make corrections in a new version;
Flyway uses checksums to detect changed migration content. Keep Hibernate in
`validate` mode and do not combine Flyway with `schema.sql`, `data.sql`, or
automatic schema updates. A migration or Hibernate validation error stops startup.
Hibernate validation is not a complete audit of indexes or CHECK constraints;
review the SQL and test database behavior as well.

Flyway does not provide an automatic undo for these SQL migrations. Prepare
forward fixes and database backups/recovery appropriate to the deployment.

The Maven dependencies are `flyway-core` and `flyway-database-postgresql`; their
versions are managed by the Spring Boot parent. Spring Boot uses the application's
datasource and Flyway's default migration location, history table, checksum
validation, and disabled automatic baseline. No separate Flyway server or Maven
plugin is needed.

## Verification

See [PostgreSQL test setup](payload-content.md#database-upgrades-and-tests).
The opt-in `FlywayMigrationPostgresTests` creates isolated schemas to check clean
installation, migration history, a second migration run with no changes, retained
rows across that second run, and rejection of a nonempty unmanaged schema.
It also checks upgrading from V1 to V2 while keeping existing catalog rows.
The application startup and existing PostgreSQL roundtrip tests run the same
migrations followed by Hibernate validation. CI enables all PostgreSQL tests.

Reference: [Spring Boot 3.5 database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html).
