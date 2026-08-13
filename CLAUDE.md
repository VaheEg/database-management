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
owns the schema — there are no migration scripts, so an entity change is the schema change.

Tests run under `@ActiveProfiles("h2")`, so the suite never needs PostgreSQL. Anything new that
boots the context should do the same.

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
response DTOs).

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

**Auth flow.** `JwtService` issues HS256 tokens carrying `uid`, `roles`, and a `jti`.
`JwtAuthFilter` rebuilds an `AppUserDetails` straight from the claims (no DB hit) and skips any
`jti` listed in `RevokedTokenStore` — that in-memory map is the only stateful part of an otherwise
stateless API, and it is what makes `POST /api/auth/logout` mean anything (a restart forgets
revocations; a multi-instance deployment would need a shared store). Roles are frozen into the
token, so a role change only takes effect at the next login.

**Secrets.** User passwords are BCrypt-hashed. External database passwords must be replayable to
the target database, so `CryptoService` encrypts them with AES-GCM (fresh IV per value) and
`DbConnectionDetailsResponse` has no password field at all. `decryptedPassword(id)` is owner-only
and exists for the not-yet-built query execution. Both `app.jwt.secret` and `app.crypto.secret`
default to dev values in `application.properties` and must be overridden via `APP_JWT_SECRET` /
`APP_CRYPTO_SECRET`; changing the crypto secret makes stored database passwords unreadable.

**Premium is verified, not asserted.** `POST /api/premium-users/request-code` mails a six-digit
code and writes *nothing* to `premium_users`; `POST /api/premium-users/confirm` checks it and only
then creates the row and grants `ROLE_PREMIUM`. There is deliberately no plain create or update —
changing a connected address goes through the same two calls. `EmailVerificationService` stores
only a BCrypt hash of the code and both it and `PremiumUserService.confirm` declare
`@Transactional(noRollbackFor = BadRequestException.class)`, because a wrong guess must commit its
incremented attempt counter; drop that and the attempt limit silently stops existing. The active
`VerificationMailSender` is chosen by `app.verification.mail-enabled` — off (the default) logs the
code instead of sending it, which is what makes the flow testable and what must never be a
deployment's configuration.

**Auditing.** `AuditLogService.record(...)` joins the caller's transaction on purpose — a separate
transaction cannot see the just-inserted row it references and deadlocks on the foreign key. The
consequence is that an audit entry rolls back with the action it describes, so `record` documents
successes; a failed attempt has to be logged from a fresh transaction after the rollback. The log
is append-only (no update/delete endpoints), and `detachConnection` nulls the link before a
connection is deleted so history survives. New mutating operations on connections or invitations
are expected to write an audit row.

**Errors.** Every failure returns the `ApiError` shape (timestamp/status/error/message/path, plus
`fieldErrors` for validation). Throw the project's `NotFoundException` / `BadRequestException` /
`org.example.exception.AccessDeniedException` — `GlobalExceptionHandler` maps them, and also
flattens both login failure modes into one "Invalid username or password" so accounts cannot be
enumerated.

## Roadmap

`src/main/resources/db-manager-project.md` is the day-by-day plan with checkboxes and is the
authority on what is done. Outstanding: the SQL-verb → `AdditionalRoleName` mapping and
`requirePermission` (Day 8), and query execution against registered databases over JDBC (Day 9);
then refresh tokens, rate limiting, and external-connection pooling. Keep that file and the
README's "Not built yet" section in step when a piece lands.
