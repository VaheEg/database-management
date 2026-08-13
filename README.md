# DB Manager — backend

Spring Boot backend for DB Manager: users register their own external databases, share
one of them with another user under a single database-scoped permission, and every
meaningful action is written to an audit log. Authentication is JWT-based and stateless.

- Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security
- Application database: **PostgreSQL**
- API documentation: **Swagger UI** at `/swagger-ui.html`

---

## 1. Point it at your database

Create an empty database once:

```bash
createdb db_manager          # or: psql -c "create database db_manager;"
```

Then set the three values in `src/main/resources/application.properties`:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/db_manager}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
```

Either edit them directly, or leave them and export `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD` instead. Nothing else has to change — Hibernate creates the tables on the
first start (`spring.jpa.hibernate.ddl-auto=update`), and `DataSeeder` inserts the two
application roles and four database permissions the rest of the app depends on.

No PostgreSQL at hand? Everything below also works against a throwaway in-memory
database:

```bash
./gradlew bootRun --args='--spring.profiles.active=h2'
```

## 2. Run it

```bash
./gradlew bootRun          # http://localhost:8080
./gradlew build            # compiles, runs the tests, builds the jar
java -jar build/libs/DatabaseManagment-0.0.1-SNAPSHOT.jar
```

## 3. Use it from Swagger

1. Open <http://localhost:8080/swagger-ui.html>
2. **1. Authentication → POST /api/auth/register** — send a username, email and a
   password of at least 8 characters. You get back a `token`.
3. Copy the `token` value and click the green **Authorize** button at the top right.
   Paste the raw token (no `Bearer ` prefix — Swagger adds it) and confirm.
4. Every other endpoint now sends that token. `GET /api/auth/me` is the quickest check.
5. **POST /api/auth/logout** revokes the token you are holding; calling anything with it
   afterwards returns 401. Log in again for a new one.

To try the premium upgrade without a mail server, call **9. Premium → POST
/api/premium-users/request-code** and read the six-digit code out of the application log
(`Verification code for … is …`), then send it to **POST /api/premium-users/confirm**.
Logging the code instead of sending it is what happens whenever
`app.verification.mail-enabled` is off, which is the default — see below.

The same flow with curl:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"password123"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

curl -s localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"
```

## 4. Endpoints

| Area | Base path | Notes |
| --- | --- | --- |
| Authentication | `/api/auth` | `register`, `login`, `me`, `logout`. Only register and login work without a token. |
| Users | `/api/users` | Anyone signed in can read; you can only change or deactivate your own account. |
| Profiles | `/api/user-information` | Your own personal details, one row per account. |
| Databases | `/api/db-connections` | Your external databases. Passwords are encrypted at rest and never returned. |
| Sharing | `/api/invitations` | Owner sends, recipient accepts or declines, sender revokes. |
| Audit log | `/api/audit-logs` | Append-only. `/connection/{id}` is the trail for one database, owner only. |
| Application roles | `/api/roles` | USER, PREMIUM — seeded at startup. |
| Database permissions | `/api/additional-roles` | SELECT/INSERT/UPDATE/DELETE_ROLE — what an invitation grants. |
| Premium | `/api/premium-users` | `request-code` then `confirm`: an email address gains the PREMIUM role once a code sent to it comes back. |

Errors always come back in the same shape:

```json
{"timestamp":"...","status":403,"error":"Forbidden","message":"Only the owner can change database connection 1","path":"/api/db-connections/1"}
```

## 5. How the security model works

- **Passwords** are BCrypt-hashed. **External database passwords** cannot be hashed —
  the backend has to present them to the other database — so they are encrypted with
  AES-GCM (`CryptoService`) and never appear in a response.
- **The caller is taken from the token, never from the request body.** Fields such as
  `ownerUserId` or `fromUserId` are optional, and sending somebody else's id is rejected
  instead of being silently honoured.
- **Ownership and grants.** The owner of a database can do anything with it. Anyone else
  needs an `ACCEPTED` invitation for *that* database; a permission on database A says
  nothing about the same owner's database B.
- **Logout** works by remembering the token's `jti` until it would have expired
  (`RevokedTokenStore`). It is in-memory, which means a restart forgets the revocations
  and a multi-instance deployment would need a shared store (Redis, or a small table).
- **Roles live in the token**, so a role change (for example becoming premium) shows up
  after the next login.
- **Becoming premium takes two calls**, because an address nobody proved they can read is
  not evidence of anything: `request-code` mails a six-digit code and writes nothing to the
  account, `confirm` checks the code and only then grants the role. Only the BCrypt hash of
  the code is stored, it expires after ten minutes, five wrong guesses destroy it, and one
  address can belong to one account. Changing a connected address goes through the same two
  calls.
- **Deactivating an account** is a soft delete: `enabled` goes to false so login stops
  working, while the audit trail and registered databases stay meaningful.

### Configuration worth overriding in a real environment

| Property | Environment variable | Why |
| --- | --- | --- |
| `app.jwt.secret` | `APP_JWT_SECRET` | Anyone holding it can mint valid tokens. Minimum 32 characters. |
| `app.crypto.secret` | `APP_CRYPTO_SECRET` | Protects stored database passwords. Changing it makes existing ones unreadable. |
| `app.jwt.expiration` | — | Token lifetime, default `12h`. |
| `app.verification.mail-enabled` | `APP_MAIL_ENABLED` | **Set it to `true`.** While it is off, verification codes are written to the application log instead of being emailed, so anyone who can read the log can verify anyone's address. |
| `spring.mail.host` and friends | — | The SMTP server the codes go through. Required once the switch above is on; without it the context will not start. |
| `app.verification.mail-from` | `APP_MAIL_FROM` | From address on the code emails. Most providers insist it be one of theirs. |
| `app.verification.code-ttl` | — | How long a code works, default `10m`. Also `max-attempts` (5) and `resend-interval` (60s). |
| `springdoc.swagger-ui.enabled` | — | Set to `false` to stop serving the docs. |

## 6. Not built yet

The parts of the plan that remain, in the order they make sense:

- **Query execution** — running SQL against a registered database with the decrypted
  credentials, mapping the result set, and rejecting statements that are not allowed.
  `DbConnectionDetailsService.decryptedPassword` and `InvitationService.hasPermission`
  are the two pieces it needs and are already in place.
- **A permission enum per SQL verb** (SELECT → `SELECT_ROLE`, and so on) so the check
  above can be driven straight from a statement.
- **Refresh tokens**, rate limiting on the query endpoint, and connection pooling and
  timeouts for the external databases.
