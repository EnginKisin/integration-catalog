# Frontend integration guide

This guide covers connections, sessions, screens, and errors for a separate
frontend project. See the [API reference](api-reference.md) for endpoint details
and the [workflow guide](integration-workflow.md) for the integration lifecycle.

The optional "Map with AI" feature supports all nine JSON/XML/IDOC pairs.
The [AI guide](ai-mapping.md) covers jobs, polling, field paths, independent target
samples, and user-approved append. The backend selects `ollama` or `gemini`.
The frontend uses the same endpoints. Do not add provider/model selection or API
key inputs. Do not send AI suggestions directly to the legacy full-list
`/mappings/fields` endpoint. Append selections through `/mappings/fields/append`.

The API root is `/api/v1`. In development, proxy `/api` from the frontend dev
server to the backend. In production, serve both under the same origin.
The backend has no cross-origin CORS setup. `credentials: "include"` alone does
not allow cross-origin access. Login does not use JWT or a localStorage token.

## Local connection and frontend project

If the frontend is in another repository, track which backend commit these guides
match. Update copied documents when the backend changes. `../README.md`,
`../scripts/`, and `../.github/` point to the backend repository root.
Do not copy backend `.env` or database/SMTP/encryption/Gemini keys to the frontend.
Users enter login credentials in a form. Browser environment variables must not
contain secrets. AI provider/model response fields are information only.

The backend defaults to `http://localhost:8080`. With Vite, add this `server`
section to your existing `vite.config.ts`:

```typescript
server: {
  port: 5173,
  strictPort: true,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

This is a config section, not a complete file. Do not add a rewrite that removes
`/api`. Browser requests should use relative `/api/v1/...` URLs, not fetch the
backend's port 8080 directly. If APP_PORT changes, update the proxy target.
In production, route `/api` to the backend and application routes to the frontend
under the same origin.

Make `/account/action` work when opened directly. Backend `ACCOUNT_ACTION_URL`
must match its full URL; the default is `http://localhost:5173/account/action`.
Apply changes to the backend container again. Using real SMTP does not create
this frontend page.

### First session and Swagger

A 401 from `/auth/me` before login is normal; show the login screen. Bootstrap
creates the first admin. Editing `.env` does not change an existing password.

Swagger and `/v3/api-docs` require an ADMIN session. Logging in through
`localhost:5173` also lets the browser use the cookie for Swagger on
`localhost:8080`, if the hostname is the same. Cookies are not separated by port.
Do not mix `localhost` and `127.0.0.1`. To proxy Swagger, also route `/swagger-ui`
and `/v3/api-docs`. Swagger's Authorize button does not replace login. Writes
still need CSRF.

## Sessions and CSRF

1. `GET /api/v1/auth/csrf`: read `headerName`, `parameterName`, and `token`. The browser keeps the cookie.
2. POST the login form as URL-encoded data with the CSRF header.
3. Successful login returns 200 with an empty body. Refresh CSRF, then read `/auth/me`.
4. Add the CSRF header to every POST/PUT/PATCH/DELETE request.
5. Logout returns 204. Clear profile/secret state in the client and get new CSRF before another login.

Anonymous invitation/password-reset/email-verification POST requests also need
CSRF. JavaScript cannot read the HttpOnly cookie. The idle timeout is 30 minutes.
Sessions are stored in PostgreSQL; an unexpired session can survive a backend
restart while the browser retains its cookie. The initial switch from in-memory
sessions requires a new login, as does account session invalidation. The
`JSESSIONID` cookie and all endpoints/request formats stay the same; no frontend
code changes are required for JDBC session storage.

This TypeScript example uses the same origin/proxy. It does not parse empty
success responses as JSON or automatically retry failed writes:

```typescript
type Csrf = { headerName: string; parameterName: string; token: string };
let csrf: Csrf | undefined;

export async function refreshCsrf(): Promise<void> {
  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'include', cache: 'no-store'
  });
  if (!response.ok) throw new Error(`Could not get CSRF token: ${response.status}`);
  csrf = await response.json();
}

export async function api<T = void>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrf) await refreshCsrf();
    headers.set(csrf!.headerName, csrf!.token);
  }
  const response = await fetch(`/api/v1${path}`, {
    ...init, method, headers, credentials: 'include', cache: 'no-store'
  });
  const text = await response.text();
  let body: unknown = undefined;
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }
  if (!response.ok) {
    throw Object.assign(new Error(`Request failed: ${response.status}`), {
      status: response.status, body, retryAfter: response.headers.get('Retry-After')
    });
  }
  return body as T;
}

export async function login(username: string, password: string): Promise<void> {
  await refreshCsrf();
  await api('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password })
  });
  csrf = undefined;
  await refreshCsrf();
}

export async function logout(): Promise<void> {
  try { await api('/auth/logout', { method: 'POST' }); }
  finally { csrf = undefined; }
}

```

Example JSON request:

```typescript
await api('/systems', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'ERP', description: 'Order system' })
});
```

Do not start login and identity-changing requests at the same time. This helper
does not replace production state management or error screens. A client timeout
does not mean the backend rolled back its work. After a lost connection, read the
current record before sending a write again.

Example `GET /auth/me`:

```json
{
  "username":"editor",
  "email":"editor@example.com",
  "emailVerified":true,
  "active":true,
  "authorities":["ROLE_EDITOR"],
  "permissions":["MANAGE_CREDENTIALS"]
}
```

Roles here have the `ROLE_` prefix. User management bodies use values such as
`EDITOR` without the prefix. Bootstrap/legacy accounts may have null email.
Show buttons based on roles; the backend still checks access on every request.
ADMIN has credential read/write access directly. Do not hide ADMIN buttons just
because the permission is not listed in `permissions`. Other users need
`VIEW_CREDENTIALS` to reveal values and EDITOR + `MANAGE_CREDENTIALS` to change them.

## Screens and data loading

| Screen | Request / behavior |
|---|---|
| Integration list | `GET /integrations?keyword=...`; returns an array without pagination |
| Catalog management | `/systems`; show `active` and `usageCount` |
| Create / basic information | Active system lists; POST to create, PUT for full updates |
| Integration detail | `GET /integrations/{id}`; includes system IDs/names and environmentTypes |
| Environment settings | Detail `environmentConfigs`; POST the whole config list |
| Schema / mapping | Separate GET schemas/fields; POST to create/update a schema or save the whole connection list |
| Payload / sample file | XML/IDOC text or upload with a field tree; CSV/Excel sample files only; [contract](payload-content.md) |
| Review | Confirm when `reviewRequired=true`, then complete |
| User management | ADMIN; `/users?page=0&size=20`, invitations, roles, active state, permissions, and session revocation |
| Profile / account actions | Password change, email linking/change, and verification page |

Load integration details first. For a missing integration, GET schemas returns
404, but GET fields may return an empty list. Do not infer integration existence
from field mappings alone. PUT bodies should include request fields only.
After saving, reload details and affected lists. Some successful writes have
empty responses. After adding/removing middleware, the first successful sample
save for the new flow removes incompatible old schemas and their mappings in
one transaction. Reload schemas/fields after text or file saves. Failed saves
keep old samples/mappings. Integration dates are server-local `LocalDateTime`
values without an offset; `updatedAt` can be null. Do not add `Z` automatically.

Keep current inactive selections in edit forms. Do not send null only because a
system is absent from the active list. `usageCount` helps the UI, but the backend
makes the final deletion check. The [workflow](integration-workflow.md) and
[system rules](systems-frontend.md) define screen behavior.

For uploads, send `metadata` as an `application/json` Blob and `file` in `FormData`.
Let the browser set multipart Content-Type. The `api()` helper above reads text/JSON.
For binary downloads, use a separate cookie-authenticated `fetch` with
`response.blob()`. GET downloads do not need CSRF. Hide mapping tools for
CSV/Excel because `mappingSupported=false`. Both ends of a mapping pair must support mapping.

Use the [frontend system contract](systems-frontend.md) for shared system choices,
unique map nodes, and environment filtering.

## Field mapping screen

Direct mappings use the same schema ID for both ends. The source side uses
`format/schemaDefinition/samplePayload/rawContent`; the independent target uses
`targetFormat/targetSchemaDefinition/targetSamplePayload/targetRawContent`.
Middleware flows have a separate target schema record. JSON/XML/IDOC formats can
be chosen independently. Build field paths according to the [AI contract](ai-mapping.md).

XML/IDOC paths are checked for existence. Full-list JSON saves also check paths
when a source sample or independent target definition/sample exists. The legacy
endpoint has limited checks for JSON source sides without samples. AI job/append
checks normalized paths. Model suggestions also pass type/array checks. Manual
connections do not guarantee automatic type conversion or semantic correctness.

## Credentials screen

Normal config responses include only `credentialsConfigured`. Do not fetch
secrets when the form first opens. Do not submit mask characters as a password.

- Keep credentials: omit `authCredentials` or send null, keeping the same environment/target/auth type.
- Replace credentials: if allowed, send a nonempty JSON map; it replaces the whole old map.
- Clear credentials: use `authType=NONE`; `{}` is not a valid clear request.
- Reveal credentials: only when the user selects "Show", GET `/integrations/{id}/configs/DEV/DESTINATION/credentials`.
  The response is `{"authCredentials":{...}}` with `Cache-Control: no-store`.

The backend does not define required map keys for BASIC_AUTH/OAUTH2/API_KEY.
A username/password or apiKey form therefore needs an agreed frontend contract;
the backend does not validate those keys semantically. Keep revealed values only
in temporary component state. Suggested behavior is to hide/clear them after
30 seconds, when closing the screen, and on logout. That delay is frontend advice,
not a backend timer. There is no backend password recheck/MFA flow for revealing.
Do not put secrets in localStorage, URLs, analytics, or application logs.

## Email link page

Links to `ACCOUNT_ACTION_URL` use a fragment:
`#purpose=INVITATION&token=...`. Read `window.location.hash`, parse it with
`URLSearchParams`, keep the token only in memory, and clear the fragment with
`history.replaceState`. Reloading after clearing it requires reopening the link.
Do not consume tokens with automatic GET requests. Send a CSRF-protected POST
after user approval.

| purpose | Screen / POST |
|---|---|
| `INVITATION` | New password; `/auth/invitations/accept` `{token,newPassword}` |
| `PASSWORD_RESET` | New password; `/auth/password-reset/confirm` `{token,newPassword}` |
| `EMAIL_CHANGE` | Address confirmation; `/auth/email/confirm` `{token}` |

Success returns 204. After invitation/reset, users log in normally. Expired/used
links return 400. ADMIN resends invitations; users request another reset.
Forgot-password requests should show the same notice for every address. A 202
does not prove email delivery. See [user management](user-management.md) for
account policies and all endpoints.

## Error handling

Backend messages are English. Localize user text with `code`, and field/completion
errors with `violations[].code` plus `params`. `message` and `details` are safe
fallback explanations, not behavior identifiers. Use your own generic error text
for an unknown code.

| HTTP | Client behavior |
|---|---|
| 400 | For `VALIDATION_FAILED`, show all field errors under the relevant inputs; other codes describe request/body errors |
| 401 | Handle login through `AUTH_REQUIRED`, `INVALID_CREDENTIALS`, or `SESSION_INVALID` |
| 403 | Separate `ACCESS_DENIED`, credential permission, and `CSRF_MISSING`/`CSRF_INVALID` |
| 404 | Show the relevant resource-not-found message |
| 409 | Use the business-rule code; list coded violations for `INTEGRATION_INCOMPLETE` |
| 410 | AI result expired; request new suggestions |
| 413 | Content/budget limit; use the code and available limit parameters |
| 422 | Correct AI/schema/field input based on the code |
| 429 | Wait for the supplied `Retry-After` seconds; this does not guarantee success |
| 500 / 503 | Show a general/temporary-service error; keep `requestId` for support |

Every response has `X-Request-ID`. HTTP error body `requestId` is the same value.
AI status can be FAILED/EXPIRED even with HTTP 200, so read `errorCode`. A successful
status GET does not need Retry-After. Poll running jobs at `pollAfterMs` intervals.

The [shared error contract](error-codes.md), [full code index](error-code-index.md),
and [example catalog](error-catalog.json) are the client's main references.

After technical choices change, the user reviews configs/schemas/mappings,
confirms review, and calls complete. Legacy config/mapping saves do not accept
ETag/If-Match or an expected version. AI context/job/append rejects an old
`contextRevision`.

## Frontend acceptance checks

- Login, wrong password, session checks after refresh, and logout work.
- Handle 401/CSRF-related 403 after expiry; writes are not retried automatically.
- Check VIEWER, EDITOR, and ADMIN actions separately from credential permissions.
- Handle catalog create/update, keeping current inactive selections, and blocking in-use deletion.
- Creation → config → schema → optional mapping → completion works. Localize 409
  `violations`. Check required schemas for all four flow types.
- Config/field saves replace whole lists. Saving one row must not accidentally
  delete others. Masked secrets must not be submitted.
- Support XML/IDOC trees/path errors, disabled CSV/Excel mapping, multipart upload,
  file download, and 413 size errors.
- Keep manual connections during AI suggestions. Handle suggestion edit/remove/approval,
  polling, stale context, and manual work after provider failure.
- Support review/recompletion after technical changes. Reload schemas/fields after
  the first successful current sample save because it cleans up old-flow data.
- Invitation/reset/email links work when opened directly. Clear fragments, POST
  with CSRF, and show expiry/reuse errors.
- Handle empty 200, JSON 201, empty/JSON 202, and empty 204 responses correctly.

Use this list for frontend acceptance. See the [architecture guide](architecture.md#test-coverage-and-validation-methods)
for backend test coverage.
