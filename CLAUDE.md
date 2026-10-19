# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

```bash
./gradlew build                                          # compile + test + jar
./gradlew bootRun                                        # needs PostgreSQL on localhost:5432/db_manager
./gradlew bootRun --args='--spring.profiles.active=h2'   # in-memory, no external DB needed
./gradlew test
./gradlew test --tests 'org.example.security.CryptoServiceTest'
./gradlew test --tests '*AuthFlowIntegrationTest.registerThenUseThenLogoutThenLoginAgain'
```

There is no linter or formatter configured. `spring.jpa.hibernate.ddl-auto=update` means Hibernate
owns the schema — there are no migration scripts, so an entity change is the schema change. The
exception is relaxing a constraint, which `update` never does: `config/SchemaUpgrade` makes those
changes at startup, after checking the catalog (today: `users.email` became nullable).

`application.properties` first imports `~/.spring/databaseManager.properties` when it exists, so a
developer machine can override anything (datasource, secrets) from outside the repo. `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD` and `SERVER_PORT` are also read from the environment.

Tests run under `@ActiveProfiles("h2")`, so the suite never needs PostgreSQL. Anything new that
boots the context should do the same. The integration tests are `@SpringBootTest` + MockMvc and
drive the real HTTP API end to end. They all share one in-memory database
(`jdbc:h2:mem:appdb;DB_CLOSE_DELAY=-1`) with no per-test rollback. Even a class that gets its own
context through `@TestPropertySource` lands in that same database, so every test must register its
own unique username and email. To make a premium user in a test, call `request-code`, read the code
back from the autowired `LoggingVerificationMailSender.lastCodeFor(email)`, then `confirm` (see
`upgradeToPremium` in `InvitationPremiumGateIntegrationTest`). The same user cannot request a
second code within `app.verification.resend-interval` (60s).

## Version-specific gotchas

Java 21, Gradle 9.5.1, **Spring Boot 4.1** — this is not the Boot 3.x world most examples assume:

- Jackson 3: the `ObjectMapper` is `tools.jackson.databind.ObjectMapper`, not `com.fasterxml.*`.
- Granular starters: `spring-boot-starter-webmvc` (not `-web`), and per-slice test starters
  (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-validation-test`).
- `@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`.
- `DaoAuthenticationProvider` takes the `UserDetailsService` in its constructor.

## Architecture

Classic layering under `org.example`: `controller` (thin, `@Valid` + Swagger annotations only) →
`service` (all business rules, authorization, and auditing) → `repository` → `entity`. DTOs are
separate request/response classes; entities never cross the HTTP boundary. Lombok is used
throughout (`@RequiredArgsConstructor` constructor injection, `@Getter/@Setter`, `@Builder` on
response DTOs). Each service maps its own entities with a hand-written `private static toResponse`;
there is no mapper library. Each controller carries a numbered `@Tag(name = "N. Area")`. Swagger
sorts tags alphabetically, so the number is what sets their order in the UI.

**Two independent role systems, deliberately not merged.**
`Role`/`RoleName` (USER, PREMIUM) are application tiers; they live in the JWT and become Spring
Security authorities (`ROLE_USER`). `AdditionalRole`/`AdditionalRoleName`
(SELECT/INSERT/UPDATE/DELETE_ROLE) are permissions on *one* `DbConnectionDetails`, granted via an
ACCEPTED `Invitation`. Database-scoped permissions are never authorities — they are checked per
request against a specific `dbConnectionId`, so a grant on database A cannot leak to database B.
`DataSeeder` inserts both enum tables idempotently at startup; the app assumes they exist.

**Authorization lives in services, not annotations.** `SecurityConfig` only distinguishes
authenticated from anonymous (`/api/auth/register`, `/api/auth/login`, Swagger, and the h2 console
are the only open paths). Everything finer-grained is a service-layer check:

- `CurrentUserService.requireId()` / `requireSelf(...)` — the caller always comes from the token.
  Client-supplied `ownerUserId` / `fromUserId` / `userId` fields are optional and are *rejected*
  when they name someone else, never silently honoured. Preserve this when adding endpoints.
- `DbConnectionDetailsRepository.findByIdAndOwnerUserId(...)` is the ownership check used by every
  write path; a hit on `existsById` afterwards is what turns a 404 into a 403.
- `InvitationService.hasPermission(userId, dbConnectionId, permission)` is the single authorization
  question for shared access: owner passes automatically, everyone else needs an ACCEPTED
  invitation carrying that permission for that database.
- Sending an invitation is premium-gated: `InvitationService.create` requires a `PREMIUM_USERS`
  row for the sender. It asks `premiumUserRepository.existsByUserId`, not the token's authorities,
  because roles are frozen into the JWT at login and an upgrade must not need a re-login to work.
  Only `create` is gated — answering, revoking and deleting stay open to the parties involved.
  The recipient is named by `toUsername` or `toUserId`; answering or revoking (`PUT`) takes an
  `InvitationStatusRequest` holding only the new status.

**Auth flow.** `JwtService` issues HS256 tokens carrying `uid`, `roles`, and a `jti`.
`JwtAuthFilter` rebuilds an `AppUserDetails` straight from the claims (no DB hit) and skips any
`jti` listed in `RevokedTokenStore` — that in-memory map is the only stateful part of an otherwise
stateless API, and it is what makes `POST /api/auth/logout` mean anything (a restart forgets
revocations; a multi-instance deployment would need a shared store). Roles are frozen into the
token, so a role change only takes effect at the next login. Anything that must take effect
immediately has to read the database, as the premium gate does.

**Deleting an account is real, not a soft delete.** `DELETE /api/users/{id}` takes the password again
(`AccountDeletionRequest`) and `UserService.delete` removes everything that exists only through the
account: its databases via `DbConnectionDetailsService.deleteAllOwnedBy` (stored passwords, and every
share of them, so guests lose access at once), the shares it received, pending codes, and by cascade
its profile, premium link and role rows. `AuditLogService.detachUser` nulls `audit_logs.user_id` first,
so other owners keep what it did on their databases. The controller then revokes the presented token;
other tokens of the account still pass the filter until they expire, but find nothing of it. The
`enabled` column and the "This account is disabled" mapping remain, though no endpoint clears it now.

**Secrets.** User passwords are BCrypt-hashed. External database passwords must be replayable to
the target database, so `CryptoService` encrypts them with AES-GCM (fresh IV per value) and
`DbConnectionDetailsResponse` has no password field at all. Only `QueryExecutionService` decrypts
one, after the permission check, so a shared user's query runs with the owner's stored
credentials. Both `app.jwt.secret` and `app.crypto.secret` default to dev values in
`application.properties` and must be overridden via `APP_JWT_SECRET` / `APP_CRYPTO_SECRET`;
changing the crypto secret makes stored database passwords unreadable.

**Queries.** `POST /api/db-connections/{id}/query` (one statement), `.../script` (`;`-separated
statements in one transaction) and `GET .../schema` open a fresh JDBC connection per call
(timeouts and the row cap come from `app.query.*`). The backend only executes: the desktop client
generates all SQL, including the CREATE/ALTER/INSERT/UPDATE/DELETE that save its table editor.
`SqlStatementGuard` is the security boundary for shared users: it lexes per `DbType` (literals,
quoted identifiers, comments), splits scripts, and maps each SELECT/INSERT/UPDATE/DELETE through
`AdditionalRoleName.forSqlVerb` to `InvitationService.requirePermission`. Anything else (DDL, a
data-modifying CTE, `SELECT ... INTO`) is owner-only — the owner holds the credentials anyway.
Every statement is authorized before the first runs; SELECT-only work runs read-only. When
changing the lexer, err towards rejecting — a `;` it fails to see is a statement it never checked.
`QueryExecutionService` is deliberately not `@Transactional`, so each audit row (successes and
`QUERY_FAILED`) commits on its own. Host and `dbName` are validated against patterns on
`DbConnectionDetailsRequest` because they are concatenated into the JDBC URL.
`POST /api/db-connections/test` and `/{id}/test` connect without saving anything, so the client can
catch a wrong host or password before it is stored. `/{id}/test` is owner-only even though it is a
read: an optional body overrides the saved settings with the stored password still used, and anyone
else could aim that password at a server of their own.

**Email is optional.** `User.email` is nullable: it only lets someone sign in with it.
`AuthService.normalizeEmail` turns a blank one into null so it never trips the unique constraint.
The premium address lives in `PREMIUM_USERS` and is verified separately.

**Premium is verified, not asserted.** `POST /api/premium-users/request-code` mails a six-digit
code and writes *nothing* to `premium_users`; `POST /api/premium-users/confirm` checks it and only
then creates the row and grants `ROLE_PREMIUM`. There is deliberately no plain create or update —
changing a connected address goes through the same two calls. `EmailVerificationService` stores
only a BCrypt hash of the code and both it and `PremiumUserService.confirm` declare
`@Transactional(noRollbackFor = BadRequestException.class)`, because a wrong guess must commit its
incremented attempt counter; drop that and the attempt limit silently stops existing. The active
`VerificationMailSender` is chosen by `app.verification.mail-enabled` — off (the default) logs the
code instead of sending it, which is what makes the flow testable and what must never be a
deployment's configuration. `request-code` says which happened, so nobody waits for an email that was
never sent. The SMTP side reads `SMTP_HOST`/`SMTP_PORT`/`SMTP_USERNAME`/`SMTP_PASSWORD`/`SMTP_AUTH`/
`SMTP_STARTTLS`; `SmtpVerificationMailSender` refuses to start without a host, and turns a refused send
into a `MailDeliveryException` (502, details only in the log). That rolls back the pending code, so the
user can ask again at once.

**Auditing.** `AuditLogService.record(...)` joins the caller's transaction on purpose — a separate
transaction cannot see the just-inserted row it references and deadlocks on the foreign key. The
consequence is that an audit entry rolls back with the action it describes, so `record` documents
successes. A failed attempt has to be logged in one of two ways. It can come from a fresh
transaction after the rollback, or it can be written inside a
`@Transactional(noRollbackFor = BadRequestException.class)` method before the throw, which is how
`EmailVerificationService` records wrong codes with `success=false`. The log
is append-only (no update/delete endpoints): `detachConnection` and `detachUser` null a link
before a connection or account is deleted so history survives, which is why `user_id` is nullable
(`record(null, ...)` is used only for the deletion itself). New mutating operations on connections or invitations
are expected to write an audit row.

**Errors.** Every failure returns the `ApiError` shape (timestamp/status/error/message/path, plus
`fieldErrors` for validation). Throw the project's `NotFoundException` / `BadRequestException` /
`org.example.exception.AccessDeniedException` — `GlobalExceptionHandler` maps them, and also
flattens both login failure modes into one "Invalid username or password" so accounts cannot be
enumerated. A `DataIntegrityViolationException` comes back as 409, an
`UnsupportedOperationException` as 501, and an `ExternalDatabaseException` (a registered database
could not be reached) as 502. A 401 or 403 raised in the filter chain, before a
controller runs, is written by `SecurityConfig`'s entry point and access-denied handler, in the same
`ApiError` shape.

## Roadmap

`src/main/resources/db-manager-project.md` is the day-by-day plan with checkboxes and is the
authority on what is done. Outstanding: refresh tokens, rate limiting on the query endpoint, and
external-connection pooling. Keep that file and the README's "Not built yet" section in step when
a piece lands.
