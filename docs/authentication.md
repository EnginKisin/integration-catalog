# Authentication and role management

The backend uses local accounts, session-based login, and three roles. Login
passwords use PBKDF2 hashes and cannot be decrypted. This is separate from the
reversible encryption used for external system credentials.

## First administrator

On first startup, set these environment variables if `user_accounts` is empty:

- `CATALOG_ADMIN_USERNAME`: 1–100 characters; letters, digits, dots, hyphens, or underscores.
- `CATALOG_ADMIN_PASSWORD`: 12–128 characters; must not contain only spaces.

There is no default username/password. The user table is checked on each startup.
If it is empty, these values create an ADMIN. Startup stops if the values are
missing or invalid. If any user exists, no new admin is created and the current
password stays the same. You can remove bootstrap variables after setup, but must
provide them again after resetting the database. Empty databases get their tables
through Flyway's [initial schema migration](database-migrations.md).

## Roles

| Action | VIEWER | EDITOR | ADMIN |
|---|---|---|---|
| Read catalog | Yes | Yes | Yes |
| Create, edit, save configs/mappings, and complete | No | Yes | Yes |
| Delete integration | No | No | Yes |
| Delete unused system | No | No | Yes |
| List and create users | No | No | Yes |
| Swagger / OpenAPI | No | No | Yes |

New endpoints are denied until access rules are added. Users join through ADMIN
invitations. See [user management](user-management.md) for roles, account state,
session invalidation, and email verification. There is no physical user deletion
or public signup. See [catalog management](systems-frontend.md) for system updates,
active status, and deletion rules.

## Frontend / Postman workflow

1. Call `GET /api/v1/auth/csrf`. Read `token` and `headerName`, and keep the session
   cookie for later requests.
2. Call `POST /api/v1/auth/login` with `application/x-www-form-urlencoded` fields
   `username` and `password`. Send the CSRF token using the returned header name.
   Success returns 200; failed login returns 401.
3. Get a new CSRF token after login. Login changes the session ID.
4. `GET /api/v1/auth/me` returns username, email, verification/active state, and
   authorities. `permissions` contains current credential permissions.
5. Send the current CSRF token and session cookie on POST/PUT/PATCH/DELETE requests.
6. `POST /api/v1/auth/logout` with CSRF returns 204 and closes the session. Get a
   fresh CSRF token before logging in again.

An ADMIN invites a user through `POST /api/v1/users` with this JSON:

```json
{"username":"reader","email":"reader@example.com","role":"VIEWER"}
```

The ADMIN does not choose the password. The account cannot be used until the user
sets a password through the email link. `GET /api/v1/users?page=0&size=20` returns
paged users (`items`, `page`, `size`, `totalElements`, `totalPages`), without
passwords or hashes. A duplicate username/email returns 409. Usernames are
case-sensitive; emails are stored in lowercase.

The session cookie is HttpOnly and SameSite=Lax, with a 30-minute idle timeout.
The frontend must use the same origin; use a development proxy locally. The
backend has no CORS setup for separate origins. Set `SESSION_COOKIE_SECURE=true`
for HTTPS. It defaults to false for local HTTP. Sessions are held in application
memory and are lost on restart. Password, email, role, credential permission, and
active-state changes increase the session version. A database check rejects old
sessions on the next request.

## External system credentials

Catalog read responses do not include `authCredentials`. The
`credentialsConfigured` field shows whether credentials exist. If a config save
omits `authCredentials`, existing credentials are kept for the same environment,
target, and auth type. A new map replaces the old map. `authType=NONE` clears them.
Changing the auth type does not carry over old credentials. Config POST replaces
the whole list, deleting omitted environment/target records. See the [frontend guide](frontend-integration.md)
for examples and masking rules.

### Encryption key and storage

Before startup, set `CATALOG_CREDENTIALS_KEY` to a Base64-encoded random 32-byte key.
You can generate it locally with `openssl rand -base64 32`. Keep it in a persistent
secret store, not source code, the database, Git, or logs. Every startup needs the
same key. Losing it makes stored credentials unreadable.

New credentials use AES-256-GCM. The existing JSONB column stores the format,
a random 12-byte IV, and encrypted content. Encryption is tied to the integration,
environment, target, and auth type. Moving or changing encrypted content causes
validation to fail. This release does not automate key rotation.

If old plaintext records exist:

1. Back up the database and stop other application instances.
2. Set the persistent key and start once with `CATALOG_CREDENTIALS_MIGRATE=true`.
3. After successful conversion, set `CATALOG_CREDENTIALS_MIGRATE=false` and keep the key.

Conversion runs in a transaction and does not encrypt existing encrypted records
again. Startup stops if plaintext records exist while conversion is off, or if
the current key cannot decrypt stored data. This does not remove plaintext from
old backups; protect their access separately. Real database checks for credential
conversion and payload storage are separate tasks.

### Separate permissions

| Permission | Effect |
|---|---|
| `VIEW_CREDENTIALS` | Read decrypted values from the protected reveal endpoint |
| `MANAGE_CREDENTIALS` | Allow an EDITOR to write, change, or delete credentials |

ADMIN has both permissions. EDITOR and VIEWER have neither by default. A VIEWER
can receive read permission. Write permission does not override the VIEWER role's
read-only limit. Neither credential permission includes the other.

ADMIN replaces all permissions with `PUT /api/v1/users/{id}/permissions`:

```json
{"permissions":["VIEW_CREDENTIALS","MANAGE_CREDENTIALS"]}
```

An empty list removes permissions. They are checked against the database, so a
removal applies to existing sessions on the next request.

The frontend's "Show" button should call
`GET /api/v1/integrations/{id}/configs/DEV/DESTINATION/credentials` only when needed.
The response has `Cache-Control: no-store`. Successful reveals log the user,
integration, environment, and target, without secret values. Secret reveal audit
uses application logs. User management also writes to `account_audit`. MFA is not
implemented.

Without `MANAGE_CREDENTIALS`, users can update config metadata such as URLs while
keeping existing secrets. New secrets, auth type changes, or removing a config
that holds secrets need that permission. Empty credential maps are rejected;
use `authType=NONE` to clear them. A new auth type needs credentials. Updates keep
the config ID and creation time.

## Validation

Login/CSRF/role tests use real security filters. Credential conversion tests use
mocked JDBC. See the [architecture guide](architecture.md#test-coverage-and-validation-methods)
for test groups and the limits of real database/SMTP coverage.

References: [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
and [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
Encryption reference: [Java 21 GCM parameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html).
