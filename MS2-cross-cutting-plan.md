# Milestone 2 — Cross-Cutting Requirements & M1 Modifications: Work-Division Plan

## Context

This plan extracts every requirement in MS2 that is **not** one of the 15 new service features (S1-F10..F12, S2-F10..F12, S3-F10..F12, S4-F10..F12, S5-F10..F12). The team has 14 members across 5 services (user 3, provider 3, booking 3, calendar 2, invoice 3). The team leader needs an inventory of cross-cutting work, broken into independently assignable sub-tasks, with parallelization guidance to avoid merge conflicts.

Sources read: `MS2.pdf` Sections 1, 2, 3, 4, 5, 6, 7, 8, 9 (CC-1..CC-6); `booking-milestone-1.pdf` (M1 baseline). The 15 service-feature specs in MS2 §10 are explicitly excluded by the user.

Source-of-truth references throughout: §X.Y refers to MS2 sections; "M1 §..." refers to MS1.

---

## Global ordering (build before features)

The auto-grader rejects any work pushed to `main` that bypasses the feature-branch flow (§2). All cross-cutting items below must be merged in a roughly fixed order so M2 features can be built on a stable base:

1. **Foundation**: Docker Compose 6 DBs (R3) + `application.yml` migration (R4) + NoSQL Spring Data dependencies/configs (R5).
2. **Auth core**: JWT infra (R6) + BCrypt password retrofit (M1.1) + Role retrofit (M1.2) + JWT filter wired on every controller (R6/R7 → CC-1).
3. **Pattern infra**: GoF Singleton (R6) + Chain of Responsibility (CC-1) + MongoEvent interface + EventFactory + per-service MongoEventLogger (Observer skeleton).
4. **M1 retrofits**: Observer wiring on every M1 write endpoint (M1.5a) + Builder retrofits on M1 DTOs (M1.5b) + Adapter retrofits where Object[] is used (M1.5c) + S5-F4 `cancellationFee=0` (M1.7) + S5-F4 `?simulateFailure=true` (M1.8) + Provider CRUD auto-index (M1.9) + Provider `serviceDetails.description` (M1.10).
5. **Caching retrofit**: Spring Cache + Redis on the 27 M1 feature GETs and 10 CRUD GET-by-ID (M1.4); wildcard-invalidation on every write (M1.4 §4.4.4).
6. **Cross-cutting endpoint**: CC-2 Role Management `PUT /api/users/{id}/role`.
7. **Now build the 15 M2 features** (out of scope for this plan).

---

## Cross-Cutting Requirement Inventory

Each item lists: name & description, sub-task breakdown, dependencies, extracted rules with section anchors, and assignment strategy.

---

### R1. Git workflow & branch hygiene continuation

**Description**: M2 keeps the M1 branch/commit conventions (§2). Each cross-cutting item below must be its own feature branch with the right scope label and the student ID suffix.

**Sub-tasks**: Indivisible (a discipline rule, not work).

**Dependencies**: None — read by every member before starting.

**Extracted rules**:
- Branch format `<type>/<scope>/<descriptor>/<studentID>`, descriptor uses stable IDs `S<n>-F<m>`, `MOD-<n>`, `CC-<n>`, or kebab-case slug for ad-hoc fixes (§2 "Branch naming convention").
- Allowed scopes: `user`, `provider`, `booking`, `calendar`, `invoice`, `m1`, `cc`, `infra` (§2).
- Cite design-pattern IDs `DP-1..DP-7` in commit messages when implementing them across multiple branches (§2 last bullet).
- Regular merge commit only — never squash; never delete branches; one branch → one PR → one work unit (§2 Rules).
- Any team member with no commits matched to a feature branch receives **zero**; one member's git mistake reflects on the entire team (§2 "Important Note").

**Assignment**: Indivisible. Team-wide policy — leader announces and enforces.

---

### R2. Project scaffolding for new dependencies

**Description**: Add the new M2 dependencies to each service's `pom.xml` (Spring Security, JJWT, BCrypt encoder, Spring Data MongoDB / Redis / Elasticsearch / Neo4j / Cassandra as appropriate per service) and the JDK-25 / Spring Boot 4.0.3 baseline (§1). Include the dual-Jackson note: Jackson 3.x for Spring Boot, Jackson 2.x retained for Hibernate 7.2 JSONB FormatMapper (§1).

**Sub-tasks** (parallelizable per service — different `pom.xml` files):
- **R2a** user-service `pom.xml`: + Security, JJWT, BCrypt, Spring Data MongoDB, Spring Data Redis.
- **R2b** provider-service `pom.xml`: + above + Spring Data Elasticsearch.
- **R2c** booking-service `pom.xml`: + above + Spring Data Neo4j.
- **R2d** calendar-service `pom.xml`: + above + Spring Data Cassandra.
- **R2e** invoice-service `pom.xml`: + above (Mongo + Redis only).

**Dependencies**: R3 not strictly required, but you cannot test until R3 (compose) is up.

**Extracted rules**:
- Spring Boot 4.0.3 on JDK 25; Docker base image `eclipse-temurin:25.0.2_10-jdk` (§1).
- Jackson dual dependency required: Jackson 3.x (`tools.jackson.*`) + Jackson 2.x (`com.fasterxml.*`) for Hibernate 7.2's JSONB FormatMapper (§1).
- PostgreSQL pinned at `postgres:17` — PG 18 breaks Hibernate (§6.1 table).
- Elasticsearch pinned at `elasticsearch:8.19.12`; Mongo/Redis/Neo4j/Cassandra use `:latest` (§6.1).

**Assignment**: 5 members in parallel, one per service, since each `pom.xml` is in a different module — zero merge-conflict risk. ~½ day of work per service. **Estimate: 5 members × 0.5 day = 2.5 person-days.**

---

### R3. Docker Compose with 6 databases (CC-5)

**Description**: Extend `docker-compose.yaml` with five new database containers (Mongo, Redis, Elasticsearch, Neo4j, Cassandra) alongside the existing PostgreSQL and 5 application services. Apply the required memory limits (§6.2).

**Sub-tasks**: Indivisible — single shared file (`docker-compose.yaml`) at the repo root. Splitting causes conflicts.

**Dependencies**: None (can start immediately).

**Extracted rules**:
- 6 services named `postgres`, `mongo`, `redis`, `elasticsearch`, `neo4j`, `cassandra` (§9.5 test (a)).
- Image tags: `postgres:17`, `elasticsearch:8.19.12`, others `:latest` (§6.1, §9.5 test (b)).
- Memory caps (§6.2 + §9.5 test (c)):
  - Redis: `--maxmemory 256mb --maxmemory-policy allkeys-lru` on the redis-server command line.
  - Elasticsearch: `ES_JAVA_OPTS=-Xms512m -Xmx512m`.
  - Cassandra: `MAX_HEAP_SIZE: 512M`, `HEAP_NEWSIZE: 128M`.
  - Neo4j: `NEO4J_server_memory_heap_max__size: 512m` (note the double underscore).
  - PostgreSQL & MongoDB: defaults.
- Healthchecks required on all 6 DBs (§9.5 test (d)) — exact commands listed in §6.4 reference fragments.
- Exact env vars for Mongo (`MONGO_INITDB_ROOT_USERNAME=root` / password `rootpass` / database `bookingmongo`), Redis (`--requirepass redispass`), Neo4j (`NEO4J_AUTH=neo4j/neo4jpass`), Cassandra (`CASSANDRA_CLUSTER_NAME=bookingcluster`, `CASSANDRA_DC=datacenter1`, `CASSANDRA_KEYSPACE=bookingks`) (§6.4).
- Named volumes: `mongo-data`, `es-data`, `neo4j-data`, `cassandra-data` (§6.4 last block).
- All 6 DBs must reach `healthy` within 120 s; total stack memory under 5 GB on `docker stats` (§9.5 tests (e)–(g)).

**Assignment**: **1 member, indivisible**, ~half a day. Branch `feat/infra/CC-5/<studentId>` or `chore/infra/...`. Pick the most ops-comfortable member (Provider or Calendar team, since they own the most exotic DBs).

---

### R4. Configuration migration `application.properties` → `application.yml` (CC-6)

**Description**: M1 used `application.properties` per service. M2 requires `application.yml` per service. The auto-grader expects YAML format (§1 "Config format" note, §6.5, §9.6).

**Sub-tasks** (parallelizable, one file per service):
- **R4a** `user-service/src/main/resources/application.yml` — PG + Redis + Mongo + JWT.
- **R4b** `provider-service/.../application.yml` — PG + Redis + Mongo + JWT + Elasticsearch.
- **R4c** `booking-service/.../application.yml` — PG + Redis + Mongo + JWT + Neo4j.
- **R4d** `calendar-service/.../application.yml` — PG + Redis + Mongo + JWT + Cassandra.
- **R4e** `invoice-service/.../application.yml` — PG + Redis + Mongo + JWT.

**Dependencies**: R3 (compose) for hostnames; R6 for the JWT secret value.

**Extracted rules**:
- `spring.datasource.url` must point to `postgres:5432` (§9.6 test (b)). PG is a hard dependency (§6.3).
- Every service must include `spring.data.mongodb.uri`, `spring.data.redis.host`, `jwt.secret`, `jwt.expiration` (§9.6 (c)).
- Service-specific connection strings (§9.6 (d)–(f), §6.5 fragments):
  - Provider: `spring.elasticsearch.uris: http://elasticsearch:9200`.
  - Booking: `spring.data.neo4j.uri: bolt://neo4j:7687` + creds `neo4j/neo4jpass`.
  - Calendar: `spring.cassandra.contact-points: cassandra` + `port: 9042` + `local-datacenter: datacenter1` + `keyspace-name: bookingks` + `schema-action: CREATE_IF_NOT_EXISTS`.
- JWT secret: at-least-32-byte (256-bit) Base64 — JJWT HS256 throws `WeakKeyException` on shorter keys; use `Keys.secretKeyFor(HS256)` or a 44-char Base64 string (§5.2). The exact same Base64 string must appear in all 5 services' `application.yml`.
- `jwt.expiration: 86400000` (24h, §6.5 reference).
- NoSQL outages must not stop service start (soft dependency); service must still serve PG-only endpoints (§6.3 + §9.6 (g)).

**Assignment**: 5 members in parallel, one per service (different files). Co-ordinate the shared JWT secret value through the team channel; everyone copies the same Base64 string. **Estimate: 5 × 0.25 day = 1.25 person-days.**

---

### R5. NoSQL entity models, repositories, and infra (no business logic)

**Description**: Define the new MongoDB document classes, the Elasticsearch document, the Neo4j nodes/relationship, and the Cassandra entity required by §7. Also wire each service's Spring Data repository interfaces. This is pure scaffolding — feature implementations consume these classes.

**Sub-tasks** (one per NoSQL store; mostly parallelizable across services):
- **R5a** Common `MongoEvent` interface — methods `getId()`, `getTimestamp()`, `getAction()`, `getDetails()` (§7.1.1). Place in a shared location (decision: copy into each service's package, since there is no shared module — services do not share code per project layout). Sub-task is "design + first implementation"; rest follow the contract.
- **R5b** `AuthEvent` (user-service, collection `auth_events`) — primary actions `REGISTERED`, `LOGGED_IN`, `ROLE_CHANGED`; non-exhaustive (§7.1.2). Implements `MongoEvent`. Includes `userId` + extension actions for M1 retrofits (`USER_UPDATED`, `USER_DEACTIVATED`, `DEFAULT_ADDRESS_SET`, `USER_CREATED`, `USER_DELETED`).
- **R5c** `ProviderEvent` (provider-service, `provider_events`) — primary `INDEXED`, `DASHBOARD_VIEWED`; retrofit `SERVICE_DETAILS_UPDATED`, `AVAILABILITY_TOGGLED`, `RATING_RECORDED`, `CERTIFICATION_VERIFIED`, `PROVIDER_CREATED`/`_DELETED` (§7.1.3).
- **R5d** `BookingEvent` (booking-service, `booking_events`) — primary `ANALYTICS_VIEWED`, `INTERACTION_RECORDED`; retrofit `PROVIDER_ASSIGNED`, `BOOKING_COMPLETED`, `BOOKING_CANCELLED`, `SERVICES_ADDED`, `BOOKING_CREATED`/`_DELETED` (§7.1.4).
- **R5e** `CalendarEvent` (calendar-service, `calendar_events`) — primary `TRACKING_RECORDED`, `ANALYTICS_VIEWED`; retrofit `SLOT_CREATED`, `BATCH_SLOTS_CREATED`, `OLD_SLOTS_PURGED`, `TIME_SLOT_DELETED` (§7.1.5).
- **R5f** `PaymentAuditEvent` (invoice-service, `payment_audit_trail`) — primary `CREATED`, `COMPLETED`, `FAILED`, `REFUNDED`, `REFUND_DENIED`, `ANALYTICS_VIEWED`; retrofit `DISCOUNT_APPLIED`, `RETRY_ATTEMPTED`, `INVOICE_DELETED`. Adds `method` + `amount` fields, **required (not null)** for `{CREATED, COMPLETED, FAILED, REFUNDED, REFUND_DENIED, DISCOUNT_APPLIED, RETRY_ATTEMPTED}`; null-permitted only for non-payment actions like `ANALYTICS_VIEWED` (§7.1.6 method/amount note). `method` enum: `CREDIT_CARD`, `CASH`, `WALLET` matching the M1 Invoice.method.
- **R5g** `ProviderSearchDocument` (provider-service, ES index `providers`) — fields per §7.2.1: `id` (Keyword), `name` (Text), `specialty` (Keyword), `pricingTier` (Keyword), `description` (Text), `rating` (Double), `status` (Keyword).
- **R5h** Neo4j `UserNode`, `ProviderNode`, and `BOOKED` relationship in booking-service — fields per §7.3 (`bookingCount`, `lastBookingDate`).
- **R5i** Cassandra `CalendarAvailabilityEvent` in calendar-service — table `calendar_availability_events`, partition key `provider_id`, clustering `timestamp DESC`, fields `date`, `total_slots`, `available_slots`, `booked_slots`, `utilization_rate`, `notes` (§7.4.1).

**Dependencies**: R2 (deps on classpath), R4 (config to connect). R5a must finish before R5b–R5f compile.

**Extracted rules**:
- MongoDB entities are **classes**, not records (§7.1 first paragraph).
- Action values are **non-exhaustive primary** values + extensible UPPER_SNAKE_CASE (each §7.1.x).
- Cassandra queries must always include `provider_id` in the WHERE clause (partition-key requirement, §7.4 final note).

**Assignment**:
- **Members A1–A5**: one per service, each owns their service's `MongoEvent` concrete class + repository (R5b–R5f). Trivially parallel — different packages.
- **Member A6** (provider team): R5g ES document.
- **Member A7** (booking team — Gohary): R5h Neo4j entities.
- **Member A8** (calendar team): R5i Cassandra entity.
- R5a `MongoEvent` interface: **first** sub-task; assign to whoever picks Singleton/Factory (R7). Once merged, R5b–R5f are unblocked.
- **Estimate: 5 service-mongo-class authors + 3 NoSQL-specialists, ~1 day each = 8 person-days.**

---

### R6. Auth & Authorization core (JWT + BCrypt + Singleton)

**Description**: Build the JWT issuance/validation infrastructure shared across all 5 services. This is the prerequisite for CC-1 (filter on every endpoint), CC-2 (role-management endpoint), and the M1.1/M1.2 retrofits.

**Sub-tasks**:
- **R6a** `JwtConfigurationManager` GoF Singleton (DP-5, §3.6) — private constructor + thread-safe `getInstance()` with double-checked locking or eager init; **not** a Spring bean (no `@Component`, `@Service`, `@Configuration`). Loads secret + expiration via env-var-with-fallback or singleton-bridge (§3.6 "Loading config in a non-Spring class"). Tests: §3.6 (a)–(f).
- **R6b** `JwtService` Spring `@Service` that issues + parses tokens. Reads config via `JwtConfigurationManager.getInstance()` (not `@Autowired`). Token payload: `sub=email`, `uid=User.id` (Long), `role`, `iat`, `exp` (§5.2). Algorithm HS256.
- **R6c** Per-service `SecurityConfig` (5 of them): stateless session, CSRF disabled, public endpoints `/api/auth/register`, `/api/auth/login` (user-service only), and health checks; everything else requires JWT (§5.4).
- **R6d** BCrypt `PasswordEncoder` bean (user-service only — issuer/verifier of credentials) (§5.5).
- **R6e** `JwtAuthenticationFilter` — Spring Security filter that delegates to the **GoF Chain of Responsibility** built in R7 inside `doFilterInternal()`.

**Dependencies**: R2 (deps), R4 (config to read secret/expiration). R6e depends on R7 (chain).

**Extracted rules**:
- JWT HS256, secret ≥32 bytes (256 bits) of entropy decoded; `WeakKeyException` thrown otherwise (§5.2).
- Header format `Authorization: Bearer <token>` (§5.2).
- JWT payload: `sub`, `uid`, `role`, `iat`, `exp` (§5.2). `uid` claim is the **only** field used for ownership checks (S1-F12, S3-F12) — direct numeric equality, no PG lookup on hot path.
- Role values stay as M1 enum (`CLIENT`, `ADMIN`); JWT carries the actual role (§5.3).
- Default role on register is `CLIENT`; `ADMIN` never assigned at register (§5.3).
- Stateless sessions, CSRF off, register/login/health public — everything else protected (§5.4).
- BCrypt hashes the password before save; plaintext never stored or returned (§5.5).
- Singleton class **must not** be Spring-annotated — graded via reflection (§3.6 test (e)).
- 10-thread `getInstance()` race must return the same reference (§3.6 test (d)).

**Assignment**:
- **Member B1** (user-service team — owns auth endpoints): R6a + R6b + R6d. ~1.5 days. The `JwtService` + `JwtConfigurationManager` are classpath-shared; consider placing them in user-service and copying the code to other services, OR writing them once in a tiny shared module if that won't violate the project's "no shared module" convention. The cleanest approach without a shared module: B1 lands them in user-service first; B2..B5 copy verbatim into their services with a doc comment pointing to the canonical copy.
- **Member B2..B6**: R6c per-service `SecurityConfig` (one per service, parallel).
- **Members on R6e**: blocked on R7 (Chain of Responsibility).
- **Estimate: ~5 person-days total (1.5 + 5 × 0.5).**

---

### R7. Design Patterns infrastructure (CC-4)

**Description**: Implement the 7 GoF patterns at the locations the spec mandates (§3, §9.4). Each pattern has its own grading test (reflection + behavioral). These can be split among members because they live in different files/packages — but Observer + Factory + Adapter compose, so plan their interfaces first.

#### R7-DP1 Strategy — Cancellation-Refund (§3.2)

- **Sub-task**: Indivisible (lives entirely inside S5-F12, which is one of the 15 features explicitly excluded by user). **Mention only**: this pattern is NOT cross-cutting; it is wholly inside S5-F12. Skip from this plan.

#### R7-DP2 Observer — Event Logging (§3.3, retrofitted across all services)

- **Description**: Classical GoF Observer (no Spring `@EventListener` writing to Mongo). Each service owns its own per-service `MongoEventLogger` instance.
- **Sub-tasks**:
  - **R7b1** `EntityObserver` interface with `onEvent(String eventType, Object payload)` (§3.3 structure).
  - **R7b2** `MongoEventLogger` concrete observer per service — bound at construction time to a fixed `EventType` (user→AUTH, provider→PROVIDER, booking→BOOKING, calendar→CALENDAR, invoice→PAYMENT_AUDIT) (§4.5 "Composition workflow" step c).
  - **R7b3** Each service's "subjects" (services) maintain an observer list with `register()` / `unregister()` and call `notifyObservers(eventType, payload)` (§3.3).
  - **R7b4** Failure policy: catch `Exception`, `log.warn()`, **never rethrow**; PG transaction must not roll back on Mongo failure (§3.3 "Failure policy").
- **Tests**: §3.3 (a)–(g).
- **Dependencies**: R5a (MongoEvent), R7-DP6 (Factory creates the concrete subtype).
- **Where applied**: All M1 writes (F2, F4, F7 per service + Booking-service-team-level S2-F8, S3-F8, S5-F5) **and** all M1 CRUD writes (§4.5 row 2 of table).

#### R7-DP3 Chain of Responsibility — JWT Filter Chain (§3.4)

- **Sub-tasks** (sequential — handlers chain together):
  - **R7c1** `AuthHandler` abstract class with `setNext(AuthHandler)` + `handle(AuthContext)`. `AuthContext` carries request, token, user, required role.
  - **R7c2** `TokenExtractionHandler` — 401 if Authorization header absent.
  - **R7c3** `SignatureValidationHandler` — 401 if invalid/expired.
  - **R7c4** `UserLoaderHandler` — 401 if user not found in PG.
  - **R7c5** `RoleAuthorizationHandler` — 403 if insufficient role for the endpoint.
  - **R7c6** Wire the chain inside `JwtAuthenticationFilter.doFilterInternal()` — short-circuit on first failure, populate `SecurityContextHolder` on success, then `filterChain.doFilter()` (§3.4 "Spring Security integration").
- **Tests**: §3.4 (a)–(h).
- **Dependencies**: R5 user repository (for UserLoaderHandler).

#### R7-DP4 Builder — Dashboard DTOs (§3.5)

- **Sub-tasks**:
  - **R7d1** Builders on the 4 M2 dashboard DTOs (`ProviderDashboardDTO`, `BookingAnalyticsDashboardDTO`, `CalendarAnalyticsDTO`, `ServiceTypeRevenueDTO`) — these are inside the 15 features, so handled by feature owners; mention only.
  - **R7d2** **M1 retrofit** — Builders on these DTO-returning M1 features with 5+ fields:
    - S1: F3, F6, F8, F9.
    - S2: F3, F6, F9 (NOT F8 — entity).
    - S3: F3, F6, F9 (NOT F8 — entity).
    - S4: F3, F6, F8, F9.
    - S5: F3, F6, F8, F9.
- **Records vs classes**: convert record to class with static inner `Builder`, OR keep record + external `<DtoName>Builder` class whose `build()` calls the canonical constructor (§3.5 "Builder with Java records").
- **Tests**: §3.5 (a)–(e).

#### R7-DP5 Singleton — `JwtConfigurationManager` (§3.6)

Already covered in R6a.

#### R7-DP6 Factory — Event Creation (§3.7)

- **Sub-task**: One `EventFactory` class per service (or one shared) with `createEvent(EventType type, Map<String, Object> params)`. Enum `EventType` with values `AUTH, PROVIDER, BOOKING, CALENDAR, PAYMENT_AUDIT`. Returns `MongoEvent`.
- **Source-scan rule**: NO `new AuthEvent(...)` / `new ProviderEvent(...)` etc. anywhere in service code — all event construction goes through the factory (§3.7 test (h)).
- **Composition with Observer** (§4.5 "Composition workflow"): on a write, service calls `notifyObservers(actionString, payload)` → `MongoEventLogger.onEvent()` → builds factory params with `params.put("action", actionString)` → calls `EventFactory.createEvent(boundEventType, params)` → persists via Spring Data.
- **Dependencies**: R5a, R5b–R5f.

#### R7-DP7 Adapter — NoSQL → DTO (§3.8) + M1 Object[] retrofit

- **Sub-tasks**:
  - **R7g1** Per-service NoSQL adapters (each has single `adapt(source) → targetDto`):
    - All services: `MongoDocumentAdapter`.
    - Provider: `ElasticsearchHitAdapter`.
    - Booking: `Neo4jRecordAdapter`.
    - Calendar: `CassandraRowAdapter`.
  - **R7g2** **M1 retrofit (conditional)** — `ObjectArrayDtoAdapter` wrapping any M1 feature that uses `Object[]` native SQL projection. **Mandatory**: S1-F3 (spec mandates Object[]). **Conditional**: any other F3/F6/F9 the team chose to implement with Object[]. Features using JPQL constructor expressions or DTO projections need NO adapter.
- **Tests**: §3.8 (a)–(f).

#### Assignment for R7 (overall)

The 7 patterns split well across the 14-member team because they live in different files. Suggested division:

| Sub-task | Owner pool | Notes |
|---|---|---|
| R7-DP3 Chain (R7c1–R7c6) | 1 user-service member | All in user-service auth package. |
| R7-DP6 Factory + R5a interface | 1 invoice-service member | Touches every service via copies; coordinate. |
| R7-DP2 Observer skeleton (`EntityObserver`, `MongoEventLogger`) | 1 user-service member | After R7-DP6 lands. Then per-service members wire `notifyObservers` calls into their own M1 writes. |
| R7-DP4 Builder retrofit | 5 members, 1 per service | Trivially parallel — each service's DTO files are isolated. |
| R7-DP7 Adapter retrofit (M1 Object[]) | Each service member who owns the affected feature | S1-F3 mandatory; others only if Object[] was chosen. |
| R7-DP7 NoSQL Adapters | 1 per service (5 members), provider/booking/calendar each get an extra adapter for their NoSQL store | Same members who own R5g/h/i can do the corresponding adapter. |
| R7-DP5 Singleton | Covered by R6a. | |

**Estimate: ~10 person-days across the patterns.**

---

### M1.1 Password Hashing (BCrypt) Retrofit (§4.1)

**Description**: Existing M1 `User.password` column in PostgreSQL must hold BCrypt hashes from M2 forward. Existing seed users re-seeded with hashed passwords (or hashed on first login). Field never serialized in responses.

**Sub-tasks**:
- **M1.1a** Re-seed mechanism: every seeded user's `password` column becomes a BCrypt hash on next seed run.
- **M1.1b** DTO/serialization filter: ensure password absent or null in `GET /api/users/{id}` and any other endpoint returning a `User` (`@JsonIgnore` on the field, or DTO with the field omitted) — covers M1 S1-F1 search, S1-F3 summary, etc. (§4.1 test (e)).

**Dependencies**: R6 (PasswordEncoder bean), and R6 must be present before re-seed can compute hashes.

**Extracted rules**:
- Stored hash starts with `$2a$`, `$2b$`, or `$2y$` and is exactly 60 characters (§4.1 test (b)).
- Plaintext password never returned in any response (§4.1, §5.5).
- Re-seeded users: every seeded user's `password` column must be a BCrypt hash, not plaintext (§4.1 test (f)).

**Assignment**: **1 user-service member** (since it touches User entity + seed logic). ~1 day. Branch `feat/m1/MOD-1/<studentId>` or `feat/user/MOD-1/...`. Coordinate with R6a/b owner so the bean is available.

---

### M1.2 Role values additive (§4.2, §5.3)

**Description**: Keep M1 enum values `CLIENT`, `ADMIN` exactly (do not rename or remove). Default role on register is `CLIENT`. Seed at least one ADMIN user.

**Sub-tasks** (indivisible):
- Confirm enum unchanged (`information_schema.columns` / `pg_enum` query in test, §4.2 (a)).
- Update seed to include at least one ADMIN row.
- Server must ignore `"role": "ADMIN"` in request body of `POST /api/auth/register` (§4.2 test (e)).

**Dependencies**: M1.1 (same touchpoint — seed mechanism).

**Extracted rules**: §4.2 (b)–(h), §5.3 table.

**Assignment**: Same person as M1.1 — bundle. Sub-day work.

---

### M1.3 JWT Authentication on existing M1 endpoints (§4.3)

**Description**: All M1 endpoints (45 feature endpoints + 10 CRUD GET endpoints + writes) must require a valid JWT, except `/api/auth/register`, `/api/auth/login`, and health checks.

**Sub-tasks**: Indivisible at the per-service level — each `SecurityConfig` (R6c) declares which paths are `permitAll()` vs `authenticated()`.

**Dependencies**: R6, R7-DP3 (chain).

**Extracted rules**:
- `/actuator/health` must return 200 without a token (§4.3 test (g)).
- All 45 M1 feature tests + CRUD tests must still pass under M2 auth (§4.3 test (h)).
- Booking exactly 3 public endpoints expected: register, login, health (§9.1 test (f)).
- Malformed (`Bearer abc`) and expired tokens both return 401 (§9.1 (c)–(d)).

**Assignment**: Folded into R6c per-service `SecurityConfig`. The R6c authors own this verification.

---

### M1.4 Redis Caching retrofit on M1 read endpoints (§4.4)

**Description**: Cache M1 read-heavy endpoints in Redis with explicit TTLs (§4.4.1, §8.1) and explicit invalidation rules (§4.4.4). Use Spring Cache abstraction or a custom service layer.

**Sub-tasks** (parallelizable per service — each service owns its own caches):
- **M1.4a** Cache-config infrastructure (one per service): Spring Cache + Redis serializer. Member co-ordinates the key convention `<service>::<entity>::<id>` and `<service>::<featureId>::<param-hash>` (§4.4.5).
- **M1.4b** Cache the 27 M1 feature GETs with the right TTLs (§4.4.1 + Note on S3-F3 + §8.1):
  - F1 = 5 min (search), F3 = 10 min (DTO), F5 = 5 min (JSONB query), F6 = 10 min (report), F8 = 15 min (relationship DTO), F9 = 10 min (combined).
  - Service breakdown (Booking-team scope):
    - S1: F1, F3, F5, F6, F8, F9 (6).
    - S2: F1, F3, F5, F6, F9 (5 — F8 is a write).
    - S3: F1, F3, F5, F6, F9 (5 — F8 is a write; F3 is the POST estimate cached by request-body hash, 5 min).
    - S4: F1, F3, F5, F6, F8, F9 (6).
    - S5: F1, F3, F6, F8, F9 (5 — F5 is a write).
  - Total: 27.
- **M1.4c** Cache the 10 CRUD GET-by-ID endpoints (one per entity), 15 min TTL (§4.4.2). Booking-platform entities: user, saved-address, provider, provider-certification, booking, booking-service, time-slot, invoice, discount, invoice-discount. **List endpoints not cached** (§4.4.2).
- **M1.4d** Wildcard-deletion invalidation on every M1 write (§4.4.4 + §4.4.6):
  - 18 feature writes total: S1 F2/F4/F7, S2 F2/F4/F7/**F8**, S3 F2/F4/F7/**F8**, S4 F2/F4/F7, S5 F2/F4/**F5**/F7.
  - 30 CRUD writes (POST/PUT/DELETE per entity × 10 entities). POST has nothing to invalidate (no cached detail yet); PUT/DELETE invalidate `<service>::<entity>::{id}` + wildcard feature keys.
  - Strategy: SCAN + DEL or KEYS+UNLINK; over-invalidation acceptable (§4.4.6).
- **M1.4e** M2-write invalidation hooks (§4.4.4 last sub-section) — **applies to M1 retrofits**:
  - CC-2 role change → invalidate `user-service::user::{id}` + `user-service::S1-F12::*`.
  - Any Booking write referencing a `providerId` → invalidate `provider-service::S2-F12::{providerId}`.
  - Any Booking create/update → invalidate `booking-service::S3-F10::*`.
  - Any TimeSlot create/update → invalidate `calendar-service::S4-F10::*`.
  - Any Invoice create/update (incl. M1 S5-F4, S5-F2, M2 S5-F12) → invalidate `invoice-service::S5-F10::*` + `invoice-service::S5-F11::*`.
- **M1.4f** NoSQL-writer → cached-reader invalidation (§4.4.4):
  - S4-F11 → `calendar-service::S4-F12::{providerId}` + `calendar-service::S4-F10::*`.
  - S3-F11 → `booking-service::S3-F12::*` (wildcard — recommendation graph changed).
  - S2-F11 + every Provider CRUD write triggering auto-index → `provider-service::S2-F10::*`.
  - Observer-driven invalidation on `payment_audit_trail` for actions ∈ {CREATED, COMPLETED, FAILED, REFUNDED, REFUND_DENIED, DISCOUNT_APPLIED} → invalidate `invoice-service::S5-F10::*` + `S5-F11::*`. Same Observer principle for `provider_events` / `booking_events` / `calendar_events` → corresponding S2-F12 / S3-F10 / S4-F10 keys.
  - **Exclude** `ANALYTICS_VIEWED` and `DASHBOARD_VIEWED` from invalidation triggers (§4.4.4 last bullet — would self-defeat the cache).

**Dependencies**: R3 (Redis up), R4 (Redis config), R5 (so the Observer Mongo-write can fire, since Observer-write triggers M2-feature-cache invalidation).

**Extracted rules** (key contract):
- Key convention: `<service>::<entity>::<id>` for entity detail; `<service>::<featureId>::<param-hash>` for feature result (§4.4.5).
- List endpoints **not cached** (§4.4.2 + §9.3 test (c)).
- TTLs: §8.1 + §4.4.1 last paragraph.
- Cache-miss graceful degradation: if Redis is down, endpoint must still return correct data from PG (§4.4.6 test (g) — soft dependency).
- 27 M1 feature GETs + 10 CRUD GET-by-ID = **37 M1 endpoints** cached for the Booking platform (§4.4.3).

**Assignment**:
- **5 members in parallel, one per service** — each service's cache config + write-invalidations are isolated to that service's own controllers.
- M1.4a (cache config) is per-service so no shared file.
- The cross-service invalidation rules in M1.4f require the Observer chain (R7-DP2) to be working before they can be tested end-to-end — so plan after R7-DP2 lands.
- **Estimate: ~2 person-days per service × 5 = 10 person-days.**

---

### M1.5 Design Pattern retrofits to M1 code (§4.5)

**Description**: The Observer / Builder / Adapter retrofits onto M1 code. Already enumerated under R7-DP2 / R7-DP4 / R7-DP7 above. Listed here for ordering clarity.

**Extracted rules** (specific to M1 retrofit):
- **Strategy NOT used in M1** — grep check: no `RefundStrategy` or `Strategy` outside the invoice-service's S5-F12 package (§4.5 test (j)).
- **Singleton NOT used in M1** — only `JwtConfigurationManager` (§4.5 table row).
- Observer fires on **every** M1 write endpoint and CRUD write — see action enumerations in R5b–R5f.
- Each of the 5 services has its own per-service `MongoEventLogger` instance — observer registration is **not** shared across services (§3.3 last bullet).
- Spring vs GoF: NO method annotated `@EventListener` in any of the 5 services may write to MongoDB (§3.3 + §4.5 test confirms via static analysis).

**Assignment**: covered above (R7-DP2 / R7-DP4 / R7-DP7).

---

### M1.6 PaymentAuditEvent population from M1 invoice writes (§4.5 "Invoice Service payment_audit_trail population")

**Description**: M1 S5-F4 (Process Invoice) and M1 S5-F2 (Process Refund) must produce events in `payment_audit_trail` so M2 S5-F11 has data. **All writes go through the Observer chain — never inline `mongoTemplate.save()` calls in service methods.**

**Sub-tasks** (indivisible — all in invoice-service):
- M1 S5-F4 must emit `CREATED` when Invoice row is inserted, and `COMPLETED` when status transitions to COMPLETED.
- M1 S5-F2 must emit `REFUNDED` when refund processed.
- Each event records `method` + `amount` (required for these payment-shaped actions per §7.1.6 method/amount note).

**Dependencies**: R5f (PaymentAuditEvent class), R7-DP2 (Observer chain), R7-DP6 (Factory).

**Assignment**: **1 invoice-service member**. ~½ day. Bundle with M1.7, M1.8.

---

### M1.7 `Invoice.transactionDetails` additive `cancellationFee` key (§4.6)

**Description**: M1 S5-F4 (Process Invoice) must write `cancellationFee = 0` into `Invoice.transactionDetails` JSONB on every new Invoice. M2 S5-F12 PartialRefundStrategy writes a non-zero value later. Pre-M2 rows without the key default to 0 in S5-F10 reads.

**Sub-tasks** (indivisible — single S5-F4 method change):
- Update S5-F4's persistence step: when constructing the JSONB map, add `transactionDetails.put("cancellationFee", 0)`.

**Dependencies**: None (just a code change to S5-F4).

**Extracted rules**:
- Numeric value, currency units (§4.6 first bullet).
- Pre-M2 backward-compat: any reader (S5-F10) treats missing/null `cancellationFee` as 0 — no DB backfill needed (§4.6 "Fallback for pre-M2 rows").

**Assignment**: **1 invoice-service member**, bundled with M1.6 + M1.8. ~½ hour task.

---

### M1.8 S5-F4 gateway-failure simulation `?simulateFailure=true` (§4.5 "Additional M1 behavior retrofits")

**Description**: M1 `POST /api/invoices/booking/{bookingId}` must accept an optional `?simulateFailure=true` query parameter. When set, S5-F4 short-circuits to `Invoice.status = FAILED` in PostgreSQL and emits a `FAILED` event to `payment_audit_trail` via Observer (no `CREATED`/`COMPLETED`).

**Sub-tasks** (indivisible).

**Dependencies**: R5f, R7-DP2.

**Extracted rules**: §4.5 + §7.1.6 FAILED action row. Used solely so S5-F11 test data can include failed payments — not a production flow.

**Assignment**: **Same invoice-service member as M1.6 / M1.7.** ~½ day total bundle.

---

### M1.9 Provider CRUD auto-index to Elasticsearch (§4.5 "Provider CRUD auto-index", §10 S2-F11 step f)

**Description**: M1's Provider CRUD controller must auto-sync the ES index on every write. POST and PUT re-index the provider's search document; DELETE removes it.

**Sub-tasks**:
- **M1.9a** Implementation via JPA `@PostPersist` / `@PostUpdate` / `@PostRemove` listener OR a service-level hook — must NOT be inlined into every CRUD controller method (§4.5 last bullet).
- **M1.9b** Tests must be able to create/update a provider via CRUD then search for it via M2 S2-F10 *without* explicitly calling `/index` (§S2-F11 step f).
- **M1.9c** Action names emitted on auto-index: `INDEXED` with `details.source = "auto_crud_create"` / `"auto_crud_update"` (§S2-F11 step e). DELETE emits `PROVIDER_DELETED` (not INDEXED).
- **M1.9d** Cache invalidation: every auto-index write triggers `provider-service::S2-F10::*` invalidation (M1.4f).

**Dependencies**: R5g (ES document), R7-DP2 (Observer).

**Assignment**: **1 provider-service member.** ~1 day.

---

### M1.10 `Provider.serviceDetails.description` additive JSONB key (§7.2 note)

**Description**: M1 `Provider.serviceDetails` JSONB does not include `description`. M2 additively extends it. Existing M1 Provider rows without the key default to empty string when read by S2-F11.

**Sub-tasks**:
- Update Provider seed/create logic to include `serviceDetails.description = ""` (or pass-through if user supplies).
- Reader (S2-F11) defaults to empty string when key missing.

**Dependencies**: None.

**Assignment**: **Same provider-service member as M1.9.** ~½ hour.

---

### CC-1 JWT on all endpoints (§9.1)

**Description**: Verification-level requirement that every endpoint across all 5 services requires a JWT except register/login/health. Composite of M1.3 (M1 endpoints) + R6c (per-service SecurityConfig) + the M2 features' own auth declarations.

**Sub-tasks**: Indivisible — folded into R6c.

**Extracted rules**:
- Per-service controller scan: count public endpoints. **Booking should have exactly 3 public endpoints** (register, login, health) (§9.1 test (f)). Any other public endpoint is a grading failure.
- Malformed token returns 401; expired token returns 401; CLIENT calling ADMIN endpoint returns 403 (§9.1).

**Assignment**: covered by R6c authors; one final integration verification by leader.

---

### CC-2 Role Management endpoint (§9.2)

**Description**: New cross-cutting endpoint `PUT /api/users/{id}/role` (ADMIN-only) that changes a user's role and logs `ROLE_CHANGED` to `auth_events`.

**Sub-tasks** (indivisible — single endpoint):
- a) Validate JWT + role claim is ADMIN → 403 if not.
- b) Find user by ID → 404 if not found.
- c) Validate requested role is a valid enum → 400 if invalid (e.g., `"BANANA"`).
- d) Update role and save.
- e) Log `ROLE_CHANGED` to `auth_events` with old + new role in details (via Observer chain).
- f) Invalidate cached user detail `user-service::user::{id}`. Note: the ROLE_CHANGED Observer write **automatically** invalidates `user-service::S1-F12::*` per §4.4.4 — no extra code in this endpoint.
- g) Return updated user with 200.

**Dependencies**: R6 (auth), R7-DP2 (Observer), R7-DP3 (chain — RoleAuthorizationHandler enforces ADMIN), M1.4 (caching).

**Extracted rules**:
- `PUT /api/users/{id}/role`, body `{"role":"ADMIN"}` or `{"role":"CLIENT"}` (§9.2 request body).
- Token staleness accepted limitation: demoted user retains old role until 24-hour expiry; promoted user must re-login. No revocation list in M2 (§9.2 "Token staleness").
- Tests: §9.2 (a)–(i).

**Assignment**: **1 user-service member.** Branch `feat/cc/CC-2/<studentId>`. ~½ day.

---

### CC-3 Redis caching on M1 endpoints (§9.3)

**Description**: Verification-level — composite of M1.4. Spot checks per Section 4.4.6.

**Assignment**: covered by M1.4 owners; final verification by leader.

---

### CC-4 Design pattern implementation (§9.4)

**Description**: Verification-level — composite of R7. See each pattern's own test scenario.

**Assignment**: covered by R7 owners.

---

### CC-5 Docker Compose with 6 databases (§9.5)

Already covered as **R3**.

---

### CC-6 Application configuration in YAML (§9.6)

Already covered as **R4**.

---

## Critical Files (modified or created)

- `docker-compose.yaml` (root) — R3.
- `pom.xml` (each of 5 services) — R2.
- `<service>/src/main/resources/application.yml` (each of 5 services) — R4.
- `<service>/.../config/SecurityConfig.java` (each of 5) — R6c.
- `<service>/.../auth/JwtConfigurationManager.java` (one canonical, copies in each service) — R6a.
- `<service>/.../auth/JwtService.java` (per service) — R6b.
- `<service>/.../auth/JwtAuthenticationFilter.java` + `auth/handlers/*.java` (chain) — R6e + R7-DP3.
- `<service>/.../observer/EntityObserver.java`, `MongoEventLogger.java` (per service) — R7-DP2.
- `<service>/.../factory/EventFactory.java`, `EventType.java` — R7-DP6.
- `<service>/.../adapter/*.java` (per service) — R7-DP7.
- `<service>/.../mongo/<XxxEvent>.java` + `<XxxEventRepository>.java` (per service) — R5b–R5f.
- `provider-service/.../search/ProviderSearchDocument.java` + repository — R5g + M1.9.
- `booking-service/.../neo4j/UserNode.java`, `ProviderNode.java`, `BookedRelationship.java` + repos — R5h.
- `calendar-service/.../cassandra/CalendarAvailabilityEvent.java` + repository — R5i.
- `user-service/.../controller/UserController.java` — CC-2 endpoint.
- `invoice-service/.../service/InvoiceService.java` — M1.6, M1.7, M1.8.
- `provider-service/.../service/ProviderService.java` (or JPA entity listener class) — M1.9.
- Every M1 controller across all 5 services — M1.4 (caching annotations) + R7-DP2 (notifyObservers calls).

---

## Verification (end-to-end)

Run sequentially after each major block lands:

1. **After R3 + R4**: `docker compose up -d` → all 6 DBs healthy in 120 s; `docker stats` total <5 GB. Each service starts with PG up only (NoSQL down) — soft-dep verification.
2. **After R6 + M1.3**: smoke-test JWT flow — `POST /api/auth/register` → 201; use returned token to call any M1 GET; missing token → 401; expired token → 401.
3. **After R7-DP2 + R7-DP6 + M1.6**: M1 S1-F2 PUT preferences → check `auth_events` has `USER_UPDATED` doc; M1 S5-F4 → `CREATED` + `COMPLETED` in `payment_audit_trail`; M1 S5-F2 → `REFUNDED` event.
4. **After M1.4**: `redis-cli KEYS '*'` after each M1 GET shows expected key; PUT/DELETE removes it; list endpoints produce no key.
5. **After M1.9**: create provider via CRUD without calling `/index`; M2 S2-F10 search returns it.
6. **After CC-2**: ADMIN promotes a CLIENT → `auth_events` has `ROLE_CHANGED`; `user-service::user::{id}` removed from Redis; `user-service::S1-F12::*` removed.
7. **Pattern reflection tests** (CC-4): grade-style reflection asserts on `RefundStrategy`, `EntityObserver`, `MongoEventLogger`, `AuthHandler` chain, `JwtConfigurationManager` (private ctor, no Spring stereotypes), `EventFactory`, every adapter class.
8. **Final M1 regression**: re-run all 21 manual M1 CRUD tests + 27 M1 feature endpoint tests under M2 auth — all must pass with valid JWT (§4.3 test (h)).

---

## Recommended assignments — all 5 service teams

Each team gets one M2 feature per member (with the calendar team's 2 members splitting 3 features). The cross-cutting load is distributed so file-level isolation minimises merge conflicts: configs, entities, observers, builders, and adapters all live in different files. Where multiple sub-tasks touch a shared controller, sequence them so the smaller diffs land first.

Items marked **shared infra** are used by other services and must land early (R5a MongoEvent interface, R6a/b JwtConfigurationManager + JwtService, R7-DP6 EventFactory, R7-DP3 AuthHandler chain, R7-DP2 EntityObserver interface). The plan's default is to copy these classes verbatim into every service after the canonical author lands them — see Resolved Clarification 3.

### User-service team (3 members: Paula 56-29253, Mahmoud 55-24394, Ragaa 49-17488)

The user team owns the auth core that every other service depends on, plus CC-2 and the M1.1/M1.2 retrofits. Land R6a + R6b + R7-DP3 first — they unblock every other team's R6c/R6e copy.

| Member | Cross-cutting share | M2 feature |
|---|---|---|
| Paula (56-29253) | **Shared infra**: R6a `JwtConfigurationManager` (GoF Singleton) + R6b `JwtService` + R6e `JwtAuthenticationFilter` + R7-DP3 Chain of Responsibility (`AuthHandler` + 4 handlers) | S1-F11 Login (consumes `JwtService` directly) |
| Mahmoud (55-24394) | R6c user `SecurityConfig` + R6d `PasswordEncoder` bean + M1.1 BCrypt retrofit + M1.2 ADMIN seed + R5b `AuthEvent` + **shared infra**: R7-DP2 Observer skeleton (`EntityObserver` interface + `MongoEventLogger` template) + Observer wiring on user M1 writes (S1-F2, F4, F7 + user/saved-address CRUD) | S1-F10 Register (BCrypt + Observer-driven `REGISTERED` event) |
| Ragaa (49-17488) | R2a pom + R4a yml + user M1.4 caching (S1-F1/F3/F5/F6/F8/F9 + user/saved-address CRUD GET-by-ID) + Builder retrofit on S1-F3/F6/F8/F9 DTOs + **mandatory** Adapter retrofit on S1-F3 Object[] (`ObjectArrayDtoAdapter` for `UserBookingSummaryDTO`) + `MongoDocumentAdapter` for user-service + CC-2 `PUT /api/users/{id}/role` endpoint | S1-F12 Activity feed (paginated Mongo read) |

### Provider-service team (3 members: Abdelrahman 68-1664, Yaseen 68-38171, Mohamed Y. 68-38204)

The provider team owns the only Elasticsearch entity + the auto-index retrofit (M1.9). Sequence M1.9 before S2-F11 testing, since the auto-index + manual /index endpoint share the same ES upsert code.

| Member | Cross-cutting share | M2 feature |
|---|---|---|
| Abdelrahman (68-1664) | R2b pom + R4b yml + R6c provider `SecurityConfig` + R5g `ProviderSearchDocument` + R7-DP7 `ElasticsearchHitAdapter` | S2-F10 Full-text search (ES query + relevance) |
| Yaseen (68-38171) | M1.9 Provider CRUD auto-index to ES (`@PostPersist`/`@PostUpdate`/`@PostRemove` listener or service hook) + M1.10 `Provider.serviceDetails.description` additive key + R5c `ProviderEvent` + Observer wiring on provider M1 writes (S2-F2/F4/F7/F8 + provider/provider-certification CRUD) + provider M1.4f cache invalidation on auto-index (`provider-service::S2-F10::*`) | S2-F11 Index Provider (explicit POST + Observer source-tagging) |
| Mohamed Y. (68-38204) | Provider M1.4 caching (S2-F1/F3/F5/F6/F9 + provider/provider-certification CRUD GET-by-ID) + Builder retrofit on S2-F3/F6/F9 DTOs (NOT S2-F8 — entity) + Adapter retrofit on any S2 F3/F6/F9 Object[] features + `MongoDocumentAdapter` for provider-service | S2-F12 Provider Performance Dashboard (Builder + DASHBOARD_VIEWED on every call) |

### Booking-service team (3 members: Gohary 55-11425, Rofaeil 55-24701, Ahmed Kamal 55-23815)

The booking team owns the only Neo4j entities and the recommendation graph. Merge-conflict hot spot is `BookingController` — sequence Rofaeil's Observer wiring first, then Ahmed Kamal's caching annotations.

| Member | Cross-cutting share | M2 feature |
|---|---|---|
| Gohary (55-11425) | R5h Neo4j entities (`UserNode`, `ProviderNode`, `BOOKED` relationship) + R7-DP7 `Neo4jRecordAdapter` + booking M1.4a cache config + booking M1.4f NoSQL-writer invalidation hooks (S3-F11 → `S3-F12::*`, any booking write → `S3-F10::*`) | S3-F12 Recommendations (Neo4j read + ownership check) |
| Rofaeil (55-24701) | R4c yml + R6c booking `SecurityConfig` + R5d `BookingEvent` + Observer wiring on booking M1 writes (S3-F2, F4, F7, F8 + booking/booking-service CRUD) | S3-F11 Record Interaction (Neo4j writer + idempotency marker) |
| Ahmed Kamal (55-23815) | R2c pom + Builder retrofit on S3-F3/F6/F9 DTOs (NOT S3-F8 — entity) + Adapter retrofit on any S3 F3/F6/F9 Object[] features + `MongoDocumentAdapter` for booking-service + booking M1.4b/c/d caching (S3-F1/F3/F5/F6/F9 + booking/booking-service CRUD GET-by-ID) + booking PG-write invalidation rules (M1.4d) | S3-F10 Analytics Dashboard (Builder + ANALYTICS_VIEWED on every call) |

### Calendar-service team (2 members: Ahmed Hussien 55-24913, Ahmed Yasser Tawfik 55-8947)

Only 2 members vs 3 features → one member owns 2 features. Pair them along the data path: snapshot writer + history reader share the Cassandra entity model and adapter, so they belong to the same person.

| Member | Cross-cutting share | M2 feature(s) |
|---|---|---|
| Ahmed Hussien (55-24913) | R2d pom + R4d yml + R6c calendar `SecurityConfig` + R5i Cassandra entity (`CalendarAvailabilityEvent`) + R7-DP7 `CassandraRowAdapter` + R5e `CalendarEvent` + Observer wiring on calendar M1 writes (S4-F2, F4, F7 + time-slot CRUD) + calendar M1.4f NoSQL-writer invalidation (S4-F11 → `S4-F12::{providerId}` + `S4-F10::*`) | **Two**: S4-F11 Availability Snapshot (Cassandra writer) + S4-F12 Availability History (Cassandra reader) |
| Ahmed Yasser Tawfik (55-8947) | Calendar M1.4 caching (S4-F1/F3/F5/F6/F8/F9 + time-slot CRUD GET-by-ID) + Builder retrofit on S4-F3/F6/F8/F9 DTOs + Adapter retrofit on any S4 F3/F6/F8/F9 Object[] features + `MongoDocumentAdapter` for calendar-service + calendar PG-write invalidation rules (M1.4d) | S4-F10 Analytics Dashboard (PG-only aggregate + ANALYTICS_VIEWED) |

### Invoice-service team (3 members: Abdelrahim 55-24423, Ali 55-6777, Ziad 55-8843)

The invoice team owns three M1 retrofits (M1.6 audit-event emission, M1.7 `cancellationFee=0`, M1.8 `?simulateFailure=true`) plus the only Strategy pattern (S5-F12). Land R5a `MongoEvent` interface + R7-DP6 `EventFactory` early — every other service depends on them.

| Member | Cross-cutting share | M2 feature |
|---|---|---|
| Abdelrahim (55-24423) | **Shared infra**: R5a `MongoEvent` interface + R7-DP6 `EventFactory` + `EventType` enum (used by all 5 services) + R5f `PaymentAuditEvent` (the most complex MongoEvent — `method`/`amount` conditional-not-null rules) + `MongoDocumentAdapter` for invoice-service | S5-F11 Payment Method Breakdown (heaviest `payment_audit_trail` reader) |
| Ali (55-6777) | M1.6 invoice audit-event emission (S5-F4 → `CREATED` + `COMPLETED`, S5-F2 → `REFUNDED` via Observer) + M1.7 `transactionDetails.cancellationFee=0` on every new Invoice + M1.8 `?simulateFailure=true` query parameter on S5-F4 + Observer wiring on invoice M1 writes (S5-F2/F4/F5/F7 + invoice/discount/invoice-discount CRUD) | S5-F12 Cancellation Refund (DP-1 Strategy: `RefundStrategy` interface + 3 concrete strategies + `RefundStrategySelector`) |
| Ziad (55-8843) | R2e pom + R4e yml + R6c invoice `SecurityConfig` + invoice M1.4 caching (S5-F1/F3/F6/F8/F9 + invoice/discount/invoice-discount CRUD GET-by-ID) + Builder retrofit on S5-F3/F6/F8/F9 DTOs + Adapter retrofit on any S5 F3/F6/F9 Object[] features + invoice M1.4f Observer-driven invalidation on `payment_audit_trail` writes (CREATED/COMPLETED/FAILED/REFUNDED/REFUND_DENIED/DISCOUNT_APPLIED → `S5-F10::*` + `S5-F11::*`; exclude ANALYTICS_VIEWED) | S5-F10 Revenue by Service Type (cross-service join + cancellationFee aggregate + Builder) |

### Cross-team sequencing (critical-path)

1. **Day 1**: R3 compose + R2 poms (5 members in parallel) + R5a MongoEvent interface (Abdelrahim).
2. **Day 2**: R4 yml (5 in parallel) + R6a/R6b (Paula) + R7-DP6 EventFactory (Abdelrahim) + R7-DP2 Observer skeleton (Mahmoud) + R5b–R5f Mongo classes (5 in parallel).
3. **Day 3**: R7-DP3 Chain (Paula) + R6c SecurityConfig in each service (5 in parallel) + R5g/h/i NoSQL entities (provider/booking/calendar).
4. **Day 4**: R6e JwtAuthenticationFilter wired (Paula's chain copied into each service) + M1.1 BCrypt + M1.2 roles (Mahmoud) + Observer wiring on M1 writes (5 in parallel).
5. **Day 5**: M1 caching retrofit (M1.4) (5 in parallel) + Builder retrofit (5 in parallel) + Adapter retrofit (5 in parallel).
6. **Day 6**: M1.6/M1.7/M1.8 (Ali) + M1.9/M1.10 (Yaseen) + CC-2 (Ragaa).
7. **Day 7+**: 15 M2 features (1 per member, 2 for Ahmed Hussien) — out of scope for this plan.

---

## Resolved clarifications

1. **Scope**: team-wide across all 5 services (14 members). The booking-service breakdown at the end is a worked example — other team leads should mirror the template (configs / security / mongo-event / observer-wiring / builder-retrofit / adapter-retrofit / caching) for their service.
2. **Strategy Pattern (DP-1)**: lives entirely inside S5-F12 (one of the 15 excluded features) and is therefore not expanded in R7. Mention only.
3. **Shared classes (`JwtConfigurationManager`, `JwtService`, `EventFactory`, `MongoEvent` interface)**: the spec is silent on grader-compliance for module structure, so this is a team judgment call. The plan's default is **copy verbatim into each service** (simplest, no parent-pom changes; 5 copies must stay in sync). Alternatives that remain open if maintenance cost grows: (a) introduce a shared `commons` Maven module, or (b) keep the canonical in user-service and have other services depend on it.
4. **M2 feature cross-references**: kept by ID only. Feature IDs S1-F10..S5-F12 anchor the dependency graph for cross-cutting work (e.g., "S2-F12 ProviderDashboardDTO needs Builder", "S4-F11 invalidates `calendar-service::S4-F12::{providerId}`"). The feature specs themselves remain out of scope.
