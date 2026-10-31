# User lifecycle

Users join through ADMIN invitations instead of public signup. The ADMIN does not
set a new user's password. Accepting an invitation verifies email ownership and
sets the password. Passwords must have 12–128 characters and must not contain only
spaces. Users log in with their username.

## Endpoints

POST/PUT requests need a CSRF token from `/api/v1/auth/csrf` and the same session
cookie, including endpoints that allow anonymous access.

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/v1/users` | ADMIN | Invite with `{username,email,role}`, 201 |
| `GET /api/v1/users?page=0&size=20` | ADMIN | Pages with up to 100 records |
| `POST /api/v1/users/{id}/invitations/resend` | ADMIN | New invitation, 202 |
| `PUT /api/v1/users/{id}/active` | ADMIN | Deactivate with `{active:false}` |
| `PUT /api/v1/users/{id}/role` | ADMIN | Change role with `{role:"EDITOR"}` |
| `PUT /api/v1/users/{id}/permissions` | ADMIN | Update credential permissions |
| `POST /api/v1/users/{id}/sessions/revoke` | ADMIN | Invalidate all sessions, 204 |
| `POST /api/v1/users/{id}/password-reset` | ADMIN | Force a password reset, 202 |
| `GET /api/v1/users/mail-status` | ADMIN | Pending and failed email counts |
| `GET /api/v1/auth/me` | Session | Profile, roles, and credential permissions |
| `POST /api/v1/auth/invitations/accept` | Token | `{token,newPassword}`, 204 |
| `POST /api/v1/auth/password-reset/request` | Public | `{email}`, same 202 response for every address |
| `POST /api/v1/auth/password-reset/confirm` | Token | `{token,newPassword}`, 204 |
| `POST /api/v1/auth/password/change` | Session | `{currentPassword,newPassword}`, 204 |
| `POST /api/v1/auth/email/change` | Session | `{currentPassword,email}`, 202 |
| `POST /api/v1/auth/email/confirm` | Token | `{token}`, 204 |

In user responses, `emailVerified`, `active`, and `passwordResetRequired` describe
the account state. Passwords, password hashes, and link tokens are not returned.

## Invitations and verification

1. ADMIN sends a username, email, and role. Email is made lowercase and must be
   unique. The account starts inactive and unverified.
2. A verification link is added to the email queue. Invitations last 24 hours.
3. The user chooses a new password in the frontend. The frontend sends the token
   and password to `/invitations/accept`.
4. The account becomes active and verified, and the token is consumed. The user
   must log in through the normal flow; login is not automatic. ADMIN can resend
   an expired invitation.

A new invitation invalidates the old link. A link for the same account and purpose
(invitation, reset, or email change) can be created at most once every 60 seconds.

## Password changes and resets

A logged-in user must verify their current password. The new password must differ
from the old one. Changing it queues a notice email and invalidates all sessions,
including the current session.

The "Forgot password" endpoint gives the same response whether an account exists
or not. It queues the same type of encrypted job for every email and checks the
account in the background. Only active, verified accounts get a 30-minute link.
This flow cannot reactivate inactive accounts. After setting a new password, all
sessions are invalidated, a notice email is queued, and the user must log in again.

An ADMIN-forced reset immediately invalidates old sessions and blocks new login
with the old password. Login is available again after the reset. The last active
ADMIN cannot be locked out this way and must use their own password-change or
forgot-password flow.

## Email changes

The current password is required. A 30-minute link is sent to the new address
while the old address stays active. A request notice is sent to the old address.
Once the new address is verified, it replaces the old one, sessions are
invalidated, and both addresses get a notice. The backend checks again that no
other account has taken the new address.

The bootstrap admin and older accounts without an email can keep logging in.
They should link an email through `/auth/email/change`. Email password recovery
is unavailable until an address is linked. This exception does not apply to new
invited users, who cannot log in before verification.

## Administration and sessions

Users are not physically deleted. Deactivating or changing the role of the last
ADMIN who can log in returns 409. Admin rows are locked in order so concurrent
requests cannot disable all administrators. Account/token changes use transactions,
and token consumption happens under a lock.

Password, email, role, and permission changes, deactivation, and session revocation
increase the account's `sessionVersion`. Real login sessions are checked against
the current database value on each request. Old versions are rejected and the
session is invalidated. If the database is unavailable during account verification,
the protected request returns 503. Health endpoints do not depend on this account check.

User actions are written to `account_audit` with the user, purpose, and time.
Passwords and raw tokens are not stored there. Audit access/retention rules and
a shared session store need separate setup.

## Tokens, email queue, and SMTP

A token is 32 secure random bytes encoded as URL-safe Base64 without padding.
It has 43 characters (`[A-Za-z0-9_-]{43}`). The token table stores only a SHA-256
hash. Validation checks the purpose, account, email, expiry, and session version.
Expired, used, or old-version links return 400.

Emails are written to `mail_outbox` in the same transaction as the account action.
The recipient, text, and raw link are encrypted with AES-GCM using
`CATALOG_CREDENTIALS_KEY`. The outbox holds an encrypted copy of the raw token;
the validation table holds only the hash. Encryption is tied to the queue row's
UUID. Keeping the key safe is also necessary for these emails.

Every 15 seconds, the worker selects up to 10 jobs. SMTP connection/read/write
timeouts are five seconds each. Failures are retried up to eight times, with
increasing delays. Successful delivery or final failure clears the encrypted
message content but keeps status and attempt counts. After an invitation's last
failed attempt, ADMIN must create a new invitation. If sending succeeds but the
process or commit fails immediately afterward, the email may be sent again with
the same single-use link. SMTP acceptance does not guarantee inbox delivery.

Email failures do not change catalog readiness. ADMIN can monitor pending and
failed counts through `/users/mail-status`. Logs contain only the email job ID
and attempt count. Do not enable SMTP debug or HTTP body/trace logging; they can
contain passwords or tokens.

Login, invitation acceptance, password, and email POST requests are limited to
20 requests per IP in 60 seconds (429). The counter is held in application memory
and resets on restart. Client-supplied X-Forwarded-For is not trusted. Multiple
instances or a reverse proxy need a shared gateway limit and trusted client IP setup.

### Local testing

```sh
docker compose --profile mail up --build -d
```

The Mailpit UI is at `http://localhost:8025`. It does not send messages to real
people. Anyone with Mailpit access can read test links; its ports are bound to
localhost only. When running from an IDE, use SMTP_HOST=localhost and
SMTP_PORT=1025. The Docker app defaults to SMTP_HOST=mailpit.

For a real server, set SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD,
SMTP_AUTH, SMTP_STARTTLS, and MAIL_FROM. A STARTTLS server typically needs
SMTP_AUTH=true, SMTP_STARTTLS=true, and the correct port. Keep credentials out of
Git. Verify SMTP acceptance and actual delivery in your own environment.

ACCOUNT_ACTION_URL is the full URL of the frontend verification page. HTTPS is
required outside localhost. The URL is not built from the Host header. The token
is in the URL fragment: `#purpose=INVITATION&token=...`. The frontend should read
it, remove it from the URL, avoid storing it, and send it to the matching POST
endpoint. Verification/reset frontend pages are not in this repository; backend
flows can be tested with an HTTP client. See the [frontend guide](frontend-integration.md)
for fragment handling and endpoint mapping, and [configuration](configuration.md)
for environment variables. Mailpit does not store messages in a volume that
survives container recreation. Do not use the local inbox as permanent audit
storage or as a backup.

## Validation limits

Account services and HTTP security filters have automated tests. SMTP tests use a
fake sender. See the [architecture guide](architecture.md#test-coverage-and-validation-methods)
for real PostgreSQL concurrency and SMTP delivery coverage.

References: [Spring Boot email](https://docs.spring.io/spring-boot/3.5/reference/io/email.html),
[OWASP password recovery](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html),
[Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
