# DB Manager — Project Overview & Daily Build Plan

## 1. Project description

**DB Manager** is a mobile application that lets users connect to and manage their own external databases through a single platform. The mobile frontend is written in Python; the backend is written in Java with Spring / Spring Boot.

A registered user stores the connection details for their own database (host, port, database name, database username, password, and database type). The backend uses those details to connect to that database and run operations on the user's behalf. Users can also share access to a database with other users, granting each collaborator a specific, database-scoped permission. Every meaningful action is recorded in an audit log.

### Core concepts

**Users and application roles.** `USERS`, `USER_INFORMATION`, `ROLES`, and `USER_ROLES` describe who a person is inside the app. `ROLES` holds general application tiers such as normal user and premium user. These are distinct from database permissions.

**Premium upgrade.** A user becomes premium by connecting an email address, tracked in `PREMIUM_USERS`. The address has to be proved first: a six-digit code goes out to it and has to be typed back, so `EMAIL_VERIFICATIONS` holds the pending challenge (hashed code, expiry, attempts) and `PREMIUM_USERS` gains its row only once the code returns.

**Database connections.** `DB_CONNECTION_DETAILS` is the heart of the app. Each row is one external database owned by one user, holding host, port, database name, database username, an encrypted password, and the database type. The backend uses this to connect to and work with that user's database.

**Sharing and delegated access.** A database owner (User A) can invite another user (User B) to access one specific database and assign a specific permission. `INVITATIONS` captures this relationship:

- `from_user_id` — the user sending the invitation
- `to_user_id` — the user receiving it
- `db_connection_id` — the database access is granted to
- `additional_role_id` — the permission being granted

**Database-scoped permissions.** `ADDITIONAL_ROLES` holds database-specific permissions such as `SELECT_ROLE`, `INSERT_ROLE`, `UPDATE_ROLE`, and `DELETE_ROLE`. These are separate from application `ROLES`. A user might be a normal application user but hold `UPDATE_ROLE` on User A's Database A. That permission applies only to that one database and never leaks to the owner's other databases.

**Auditing.** `AUDIT_LOG` records which user performed an action, what the action was, which database was involved, whether it succeeded, and when it happened.

### Central flow

```
User A  →  owns Database A  →  invites User B  →  grants User B a specific database permission
```

User B can then perform only the granted operation, and only on Database A.

### Stack

- **Frontend:** Python (mobile)
- **Backend:** Java 17+, Spring Boot, Spring Data JPA, Spring Security (JWT)
- **App database:** PostgreSQL or MySQL (stores users, roles, connections, invitations, audit log)
- **External databases:** connected at runtime using the stored connection details

---

## 2. Daily build plan

> **Status:** Days 1–10 are done, and with them the parts of Day 11 that the JWT
> work touches (DTO validation, Swagger/OpenAPI, secrets from the environment).
> `POST /api/auth/logout` revokes a token. What remains is the rest of Day 11;
> see "Not built yet" in the README.

One part per day, in dependency order. Each part builds on the ones before it, so work top to bottom. Check items off as you go.

### Day 1 — Project setup & skeleton
*Goal: an empty app that starts up.*

- [x] Create Spring Boot project (Spring Initializr or Maven `pom.xml`)
- [x] Add dependencies: `web`, `data-jpa`, `security`, `validation`, JWT library, DB driver, Lombok
- [x] Create packages: `config`, `controller`, `dto`, `entity`, `repository`, `security`, `service`, `exception`, `audit`
- [x] Write `application.yml` (port, datasource, JPA settings) — as `application.properties`, plus an `h2` profile for running without PostgreSQL
- [x] Add main `@SpringBootApplication` class
- [x] Confirm the app starts with no errors

**Done when:** `./gradlew bootRun` boots cleanly on an empty database.

### Day 2 — Entities (schema in code)
*Goal: your ER diagram expressed as JPA entities.*

- [x] `User` (id, username, password hash, enabled, createdAt)
- [x] `UserInformation` (one-to-one with User)
- [x] `Role` (application roles: USER, PREMIUM)
- [x] `USER_ROLES` mapping (many-to-many User↔Role)
- [x] `PremiumUser` (one-to-one with User, email)
- [x] `EmailVerification` (user, email, hashed code, expiresAt, attempts) — the pending premium challenge
- [x] `DbConnectionDetails` (owner, host, port, dbName, dbUsername, encrypted password, dbType)
- [x] `DbType` enum (POSTGRESQL, MYSQL, …)
- [x] `AdditionalRole` (SELECT_ROLE, INSERT_ROLE, UPDATE_ROLE, DELETE_ROLE)
- [x] `Invitation` (fromUser, toUser, dbConnection, additionalRole, status)
- [x] `InvitationStatus` enum (PENDING, ACCEPTED, DECLINED, REVOKED)
- [x] `AuditLog` (user, dbConnection, action, detail, success, performedAt)

**Done when:** Hibernate creates/validates every table against your schema.

### Day 3 — Repositories
*Goal: data access for every entity.*

- [x] `UserRepository` (findByUsername, existsByUsername)
- [x] `RoleRepository` (findByName)
- [x] `AdditionalRoleRepository` (findByName)
- [x] `PremiumUserRepository`
- [x] `EmailVerificationRepository` (newest pending challenge per user; delete by user)
- [x] `DbConnectionDetailsRepository` (findByOwnerId)
- [x] `InvitationRepository` (find by toUser / fromUser; the key authorization lookup)
- [x] `AuditLogRepository` (find by connection / user, ordered by time)

**Done when:** all repositories inject and basic finder methods work.

### Day 4 — Security & authentication (JWT)
*Goal: register, log in, and protect endpoints.*

- [x] `PasswordEncoder` bean (BCrypt)
- [x] `JwtService` (generate & validate tokens) — tokens carry `uid`, `roles` and a `jti`
- [x] `UserDetails` + `UserDetailsService` implementations
- [x] `JwtAuthFilter` (reads Bearer token, sets security context, refuses revoked tokens)
- [x] `SecurityConfig` (stateless, permit register/login and the Swagger paths, secure the rest)
- [x] `DataSeeder` (seed roles + additional roles on startup)
- [x] `RevokedTokenStore` — what makes `POST /api/auth/logout` mean something

**Done when:** unauthenticated requests to protected routes return 401.

### Day 5 — Auth endpoints
*Goal: users can sign up and get a token.*

- [x] Auth DTOs (RegisterRequest, LoginRequest, AuthResponse)
- [x] `AuthService` (register: hash password, assign default role; login: authenticate + issue token; logout: revoke the presented token)
- [x] `AuthController` (`POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`)
- [x] Global exception handler + custom exceptions (NotFound, BadRequest, AccessDenied)

**Done when:** you can register, log in, and call a protected endpoint with the token.

### Day 6 — Database connections
*Goal: users store their own DB credentials safely.*

- [x] `CryptoService` (AES-GCM encrypt/decrypt for stored DB passwords)
- [x] `CurrentUserService` (resolve the logged-in user)
- [x] Connection DTOs (never return the password)
- [x] `ConnectionService` (create, list mine — passwords encrypted at rest) — `DbConnectionDetailsService`
- [x] `ConnectionController` — `DbConnectionDetailsController`, full CRUD on `/api/db-connections`

**Done when:** a user creates a connection and the password is stored encrypted, never returned.

### Day 7 — Invitations & sharing
*Goal: owners grant scoped access to other users.*

- [x] Invitation DTOs
- [x] `InvitationService.create` (only the owner can share their DB)
- [x] `respond` — `update` with status ACCEPTED / DECLINED, recipient only
- [x] `revoke` — `update` with status REVOKED, sender only
- [x] incoming / outgoing listing
- [x] `InvitationController` (send, answer, revoke, list) on `/api/invitations`
- [x] Invite by username (`toUsername`), and responses that carry names (sender, recipient, permission, database) so a client can show them without more calls
- [x] The desktop client: Share on an owned database, Invitations to accept or decline, Go Premium to verify an address

**Done when:** User A can grant User B a permission on one DB, and B can accept it.

### Day 8 — Authorization (the permission model)
*Goal: enforce database-scoped permissions.*

- [x] `DbPermission` enum + map SQL verb → required permission — `AdditionalRoleName.forSqlVerb`, driven by `SqlStatementGuard`
- [x] `isOwner` — `DbConnectionDetailsRepository.findByIdAndOwnerUserId`, used by every write path
- [x] `hasPermission` (owner has all; others need an ACCEPTED invitation for THAT db) — `InvitationService.hasPermission`
- [x] `requirePermission` (throws 403 if missing) — `InvitationService.requirePermission`
- [x] Verify a permission on DB-A never leaks to the owner's DB-B — the lookup is scoped by `dbConnectionId`

**Done when:** B with UPDATE on DB-A cannot touch DB-B, and cannot DELETE on DB-A.

### Day 9 — Query execution
*Goal: run SQL against the user's external DB, permission-checked.*

- [x] Query DTOs — `QueryRequest`, `QueryResponse` (plus `TableSchemaResponse` for `GET .../schema`)
- [x] `QueryExecutionService`: detect required permission → check authorization → open JDBC connection with decrypted creds → run → map results
- [x] `QueryController` (`POST /api/db-connections/{id}/query`, `GET /api/db-connections/{id}/schema`)
- [x] Reject unsupported/dangerous statements — shared users get SELECT/INSERT/UPDATE/DELETE only; DDL and everything else is owner-only; SELECTs run read-only
- [x] `POST /api/db-connections/{id}/script` — several statements in one transaction (the desktop client saves its table editor this way)
- [x] `POST /api/db-connections/test` and `/{id}/test` — check settings before they are saved (the desktop client tests on add, edit and sign-up, and offers to fix a database that cannot be reached)

**Done when:** an authorized user runs a query on their external DB and gets rows back.

### Day 10 — Audit logging
*Goal: record who did what, where, and when.*

- [x] `AuditLogService.record` — joins the caller's transaction on purpose; the javadoc explains why a separate one deadlocks on the new row's foreign key
- [x] Wire audit calls into connection create/update/delete, every invitation change, and every query attempt
- [x] `AuditLogController` (`GET /api/audit-logs/connection/{id}` — owner only)

**Done when:** every meaningful action leaves an audit row visible to the owner.

### Day 11 — Hardening & polish
*Goal: production readiness (ongoing).*

- [x] Input validation on all DTOs (`@Valid`, constraints)
- [x] Email optional at registration — only premium needs an address, and it verifies its own; `SchemaUpgrade` drops the old NOT NULL on `users.email`
- [x] Email verification for the premium upgrade — `POST /api/premium-users/request-code` then `/confirm`; hashed six-digit code, 10-minute expiry, 5 attempts, 60-second resend interval, and one address per account
- [x] Codes go out by email once `APP_MAIL_ENABLED` and the `SMTP_*` settings are given; while mail is off, `request-code` says the code went to the log; a refused send is a 502
- [ ] Turn `app.verification.mail-enabled` on with real SMTP settings before any deployment — while it is off the codes go to the application log
- [x] Account deletion — password asked again; the account's databases, shares, premium link and profile go with it; audit entries stay without it
- [ ] Connection pooling / timeouts for external DBs — timeouts done (`app.query.*`), pooling not
- [ ] Refresh tokens + token expiry handling — logout/revocation done, refresh tokens not
- [x] SQL injection & statement whitelisting review — `SqlStatementGuard`; host/dbName validated so they cannot inject JDBC URL options
- [ ] Rate limiting on the query endpoint
- [ ] Unit + integration tests per service — auth flow, premium upgrade, sharing and `CryptoService` covered so far
- [x] API docs (Swagger / OpenAPI) — `/swagger-ui.html`, with an Authorize button for the JWT
- [x] Secrets from env vars, not config files — `APP_JWT_SECRET`, `APP_CRYPTO_SECRET`, `DB_*`

**Done when:** you'd be comfortable pointing the mobile app at it.

---

## 3. Dependency map

```
Day 1 (setup)
  └─ Day 2 (entities)
       └─ Day 3 (repositories)
            └─ Day 4 (security)
                 └─ Day 5 (auth endpoints)
                      └─ Day 6 (connections)  ← needs CryptoService
                           ├─ Day 7 (invitations)
                           │    └─ Day 8 (authorization)
                           │         └─ Day 9 (query execution)
                           └─ Day 10 (audit)  ← wired across Days 6–9
                                └─ Day 11 (hardening)
```
