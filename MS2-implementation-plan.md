# MS2 Implementation Plan — Pre-Feature Foundation

> **Companion to** `MS2-cross-cutting-plan.md` (kept as-is for the strategic / work-division view). This document is the **executable** plan: every phase below is a sequence of branch-scoped tasks any agent can pick up and finish without context from the rest of the project.
>
> **Audience**: any single agent (human or AI) can pick up any phase by running Phase 0 first to pin a `<ID>`, then executing the phase's steps. Owners are deliberately not assigned — the team leader maps phases to members separately.
>
> **Out of scope**: the 15 new M2 features (S1-F10..F12, S2-F10..F12, S3-F10..F12, S4-F10..F12, S5-F10..F12). Those are picked up only after Phase 11 lands.
>
> **Spec authority**: `MS2.pdf` (sections referenced inline as §X.Y) + `booking-milestone-1.pdf`.

---

## Phase 0 — Session preflight (every agent, every session)

Before touching code, the agent must capture the operator's identity and pin it for the entire session. Every branch name and commit subject ends with this ID; getting it wrong invalidates auto-grader credit (§2 "Important").

### 0.1 Agent boot script

At the start of any session that will produce a git artifact, the agent runs this dialogue once:

```
Agent: "I need your numeric student ID before any commit. Format: NN-XXXXX
        (e.g. 55-11425, 56-29253, 49-17488, 68-1664), matching team.json at the
        repo root. I will use it for every branch name and commit message in
        this session."
User:  "<ID>"
Agent: [validates against team.json:
        $ jq -r '.[].studentId' team.json | grep -x '<ID>'
        On no match → error and re-ask.
        On match → store as $STUDENT_ID for this session.]
```

The agent **must** verify the supplied ID against `team.json` at the repo root (already exists, 14 entries). If the ID is not in the file the agent stops and surfaces the mismatch — never invent or fall back to a different ID.

### 0.2 Pinning the ID for the session

Once validated, the agent treats `$STUDENT_ID` as a session constant. Every subsequent shell snippet in this document uses `<ID>` as the literal placeholder; the agent substitutes `$STUDENT_ID` at execution time. **Never hard-code an ID in this file**.

### 0.3 git config sanity check

```bash
git config user.name        # must be the human's GitHub display name
git config user.email       # must match the team.json githubUsername's email
```

If unset, the agent surfaces the gap and asks the user to set them. Do **not** modify `git config` autonomously (CLAUDE.md rule, and §2 auto-grader matches on author).

---

## Phase 1 — Branch & commit conventions (read once, follow forever)

§2 of MS2 is enforced by the auto-grader. Every artifact in this plan obeys these rules; the agent re-checks them before every `git commit` and `git push`.

### 1.1 Branch name format

```
<type>/<scope>/<descriptor>/<ID>
```

| Position | Allowed values |
|---|---|
| `<type>` | `feat`, `fix`, `hotfix`, `refactor`, `docs`, `test`, `chore`, `perf` |
| `<scope>` | `user`, `provider`, `booking`, `calendar`, `invoice` (per-service work); `m1`, `cc`, `infra` (cross-cutting) |
| `<descriptor>` | Stable ID `S<n>-F<m>`, `MOD-<n>`, `CC-<n>`, `DP-<n>`; or kebab-case slug for ad-hoc fixes |
| `<ID>` | Numeric student ID exactly as in `team.json`, e.g. `55-11425` |

**Examples this plan uses**:
- `feat/infra/CC-5/<ID>` — Docker Compose with 6 DBs
- `feat/<service>/CC-6/<ID>` — application.yml migration (per-service)
- `feat/cc/DP-5/<ID>` — Singleton infrastructure (cross-cutting)
- `feat/<service>/MOD-1/<ID>` — BCrypt password retrofit
- `feat/cc/CC-2/<ID>` — Role management endpoint

### 1.2 Commit message format (Conventional Commits)

```
<type>(<scope>): <imperative subject ≤72 chars> (<ID>)
```

Example: `feat(user): MOD-1 hash existing passwords with BCrypt (55-11425)`

**Hard rules** (§2):
- Imperative mood (`add`, not `added`).
- No trailing period.
- `<ID>` in parentheses at end of subject — every commit attributable.
- **Never** add `Co-Authored-By` lines.
- When implementing a design pattern across multiple branches, cite the `DP-<n>` ID in **every** commit that touches that pattern, e.g. `feat(invoice): MOD-6 emit CREATED via Observer (DP-2) (<ID>)`.

### 1.3 PR + merge rules

- One branch → one PR → one work unit. Do not bundle.
- At least one teammate reviews and approves.
- **Regular merge commit only** — never squash-merge on GitHub. The auto-grader extracts the branch name from the merge commit.
- **Never delete the branch after merging** — §2 reinforces this; deletion blocks credit.
- One member's git mistake reflects on the entire team (§2 Important Note). Treat every branch + PR as a team-wide checkpoint.

### 1.4 Cross-cutting branch strategy for design patterns

Design patterns (DP-1..DP-7) are scattered: their **infrastructure** lives in one place, but their **applications** are sprinkled across feature/MOD branches. Strategy used throughout this plan:

| Concern | Branch |
|---|---|
| Pattern infrastructure (interface, abstract class, canonical singleton, chain skeleton, factory class) | Dedicated `feat/cc/DP-<n>/<ID>` branch |
| Pattern *application* on a specific M1 retrofit | The MOD branch that owns the retrofit, with the `DP-<n>` ID cited in every commit message |
| Pattern application on an M2 feature | The feature branch that owns the feature, with the `DP-<n>` ID cited |

This satisfies §2's last bullet ("Where a design pattern is implemented across multiple branches, cite the DP ID in each commit message") and gives the auto-grader at least one branch with `DP-<n>` in its name per pattern, so the pattern is unambiguously claimable.

### 1.5 Verbatim copy strategy for shared classes

The team chose **Option 1 — verbatim replication**. Classes used by all 5 services (`JwtConfigurationManager`, `JwtService`, `JwtAuthenticationFilter`, the 4 chain handlers, `EntityObserver` interface, `Observable` base class, `MongoEvent` interface, `EventType` enum, `EventFactory`) are authored in one canonical service, then copied byte-for-byte into the other four — only the `package` declaration differs.

Each verbatim copy is a separate commit on the destination service's branch:
```
chore(<service>): verbatim copy of <ClassName> from <canonical-service> (<ID>)
```
This makes drift auditable on review.

---

## Phase 2 — Foundation infrastructure

This phase contains every prerequisite that the rest of the plan compiles against. **Nothing else runs until Phase 2 is fully merged.** Items 2A–2C run in parallel (different files); 2D depends on 2A.

### 2A — `feat/infra/CC-5/<ID>` Docker Compose with 6 databases

Indivisible — one shared `docker-compose.yaml`.

**Dependencies**: None.

**Files modified**:
- `/Users/gohary/repos/28-SpringShoes-Booking/docker-compose.yaml` (existing — extend, don't rewrite the PG section)

**Implementation steps**:

1. Open `docker-compose.yaml` and verify the existing `postgres` service uses `image: postgres:17` (NOT `latest`, NOT `18` — §6.1 mandates 17; PG 18 breaks Hibernate).
2. Add five new top-level services under `services:` (order does not matter, but keep alphabetical for readability):
   - `mongo` → `mongo:latest`, port `27017:27017`, env `MONGO_INITDB_ROOT_USERNAME=root`, `MONGO_INITDB_ROOT_PASSWORD=rootpass`, `MONGO_INITDB_DATABASE=bookingmongo`, volume `mongo-data:/data/db`, healthcheck `mongosh --eval "db.adminCommand('ping')"` interval 10s timeout 5s retries 5.
   - `redis` → `redis:latest`, port `6379:6379`, **command** `redis-server --requirepass redispass --maxmemory 256mb --maxmemory-policy allkeys-lru` (the command-line flag is graded — §9.5(c)), healthcheck `redis-cli -a redispass ping` interval 5s.
   - `elasticsearch` → `elasticsearch:8.19.12` (pinned — §6.1, §9.5(b)), port `9200:9200`, env `discovery.type=single-node`, `xpack.security.enabled=false`, **`ES_JAVA_OPTS=-Xms512m -Xmx512m`** (graded), volume `es-data:/usr/share/elasticsearch/data`, healthcheck `curl -f http://localhost:9200/_cluster/health || exit 1` start_period 30s.
   - `neo4j` → `neo4j:latest`, ports `7474:7474` and `7687:7687`, env `NEO4J_AUTH=neo4j/neo4jpass`, **`NEO4J_server_memory_heap_max__size=512m`** (note the **double underscore** before `_size` — single underscore is silently ignored by Neo4j), `NEO4J_server_memory_heap_initial__size=256m`, healthcheck `neo4j status` start_period 30s.
   - `cassandra` → `cassandra:latest`, port `9042:9042`, env `CASSANDRA_CLUSTER_NAME=bookingcluster`, `CASSANDRA_DC=datacenter1`, `CASSANDRA_KEYSPACE=bookingks`, **`MAX_HEAP_SIZE=512M`** + **`HEAP_NEWSIZE=128M`** (graded), volume `cassandra-data:/var/lib/cassandra`, healthcheck `cqlsh -e 'DESCRIBE KEYSPACES'` start_period 60s.
3. Add the four named volumes at the bottom: `mongo-data`, `es-data`, `neo4j-data`, `cassandra-data`.
4. Verify each of the 5 application services already has `depends_on` for `postgres`. Add `depends_on` for the new DBs **only** in the service that uses them (provider depends on `elasticsearch`, booking depends on `neo4j`, calendar depends on `cassandra`, all five depend on `mongo` and `redis`). Use `condition: service_healthy` so the app waits for the DB.

**Gotchas**:
- The Neo4j env var name has **two underscores** between `heap_max` and `size`. Single-underscore variants are ignored without warning, leaving the JVM with the default 1+ GB heap. Same for `heap_initial__size`.
- Do not use `mongo:7-jammy` or any other tag — the spec literally says `latest` (§6.1). The grader regex-matches on the tag.
- Cassandra needs `start_period: 60s`. Without it the healthcheck flaps for the first ~45 seconds and dependent services may fail to start.
- Redis password (`redispass`) must match exactly across `docker-compose.yaml` and every `application.yml`. Mismatch fails connect with `NOAUTH Authentication required` — easy to miss because the soft-dependency policy lets the service still start.

**Branch + commit**:
```bash
git checkout main && git pull origin main
git checkout -b feat/infra/CC-5/<ID>
# edit docker-compose.yaml
git add docker-compose.yaml
git commit -m "feat(infra): CC-5 add 6-database stack with memory caps and healthchecks (<ID>)"
git push -u origin feat/infra/CC-5/<ID>
gh pr create --base main --title "feat(infra): CC-5 6-database compose stack" --body "..."
```

**MS2 test scenarios (§9.5)**:
- (a) `docker compose config` lists services `postgres`, `mongo`, `redis`, `elasticsearch`, `neo4j`, `cassandra`.
- (b) Image tags: `postgres:17`, `elasticsearch:8.19.12`, others `:latest`.
- (c) Memory caps applied as listed above.
- (d) Healthchecks present on all 6.
- (e) `docker compose up -d` reaches all-healthy in <120 s.
- (f) All 5 application services start successfully (deferred — runs after 2B/2D).
- (g) `docker stats` total memory <5 GB.

**Additional integration tests** (write these as a `bash` script `scripts/verify-compose.sh` and commit alongside):
```bash
docker compose down -v                                          # clean slate
docker compose up -d
sleep 90
for svc in postgres mongo redis elasticsearch neo4j cassandra; do
  docker compose ps $svc --format '{{.Health}}' | grep -q healthy || { echo "FAIL: $svc not healthy"; exit 1; }
done
docker exec booking-redis redis-cli -a redispass ping | grep -q PONG
docker exec booking-mongo mongosh -u root -p rootpass --authenticationDatabase admin --eval 'db.adminCommand("ping").ok'
docker exec booking-neo4j cypher-shell -u neo4j -p neo4jpass 'RETURN 1'
docker exec booking-cassandra cqlsh -e "DESCRIBE KEYSPACES;"
curl -fsS http://localhost:9200/_cluster/health | grep -q '"status":"\(green\|yellow\)"'
```

**Verification loop**: run the script. If any assertion fails, `docker compose logs <service>` to triage; common fixes — bump start_period, fix env-var underscore count, fix port collision. Re-run until all assertions pass before opening the PR.

---

### 2B — `feat/<service>/CC-6/<ID>` application.yml migration (×5)

Five branches, one per service, run in parallel.

**Dependencies**: 2A merged (so the agent can validate hostnames against compose).

**Per-service files**:
- `<service>/src/main/resources/application.yml` (new)
- `<service>/src/main/resources/application.properties` (delete after migration is verified)

**Implementation steps**:

1. Read the existing `application.properties` for the service.
2. Create `application.yml` with the layered structure shown in §6.5. Common fragment for every service:
   ```yaml
   server:
     port: 8080                    # internal; docker-compose maps to 808X externally
   spring:
     application:
       name: <service-name>        # e.g. booking-service
     datasource:
       url: jdbc:postgresql://postgres:5432/bookingdb
       username: postgres
       password: postgres
     jpa:
       hibernate:
         ddl-auto: update
       show-sql: true
     data:
       redis:
         host: redis
         port: 6379
         password: redispass
       mongodb:
         uri: mongodb://root:rootpass@mongo:27017/bookingmongo?authSource=admin
   jwt:
     secret: ${JWT_SECRET:fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=}   # 44-char Base64 = 32 bytes; same value across all 5 services
     expiration: 86400000          # 24h
   ```
3. Append the service-specific block:
   - **provider**: `spring.elasticsearch.uris: http://elasticsearch:9200`
   - **booking**: `spring.data.neo4j.uri: bolt://neo4j:7687`, `spring.data.neo4j.authentication.username: neo4j`, `spring.data.neo4j.authentication.password: neo4jpass`
   - **calendar**: `spring.cassandra.contact-points: cassandra`, `spring.cassandra.port: 9042`, `spring.cassandra.local-datacenter: datacenter1`, `spring.cassandra.keyspace-name: bookingks`, `spring.cassandra.schema-action: CREATE_IF_NOT_EXISTS`
4. Delete `application.properties` (`git rm`).
5. Verify the service still boots: `cd <service> && mvn spring-boot:run` (with compose up). Hibernate startup logs should mention "Database version: PostgreSQL 17.x".

**Gotchas**:
- The same JWT secret value must appear in **all five** services. Coordinate the Base64 string in the team chat **before** any agent edits a yml. JJWT HS256 throws `WeakKeyException` on a Base64 string shorter than 44 characters when decoded — the example string above is 44 chars / 32 bytes / 256 bits.
- The compose `postgres` service is reachable as the hostname `postgres` from inside the Docker network. From the host (running mvn outside Docker) it is `localhost`. For local dev, use `${POSTGRES_HOST:postgres}` indirection if you want the same yml to work both ways.
- Spring Boot 4.0.3 still loads `application.properties` if both files exist — the grader checks for the absence of `.properties`. Make sure the `git rm` happened.
- The yml uses 2-space indentation. Tabs cause `ScannerException`. The Spring `spring.data.redis.*` keys are correct — the older `spring.redis.*` keys are deprecated and will not bind in Boot 4.x.
- Soft-dep verification: stop only Mongo/Redis/ES/Neo4j/Cassandra and start the service — must boot without throwing. Only PG-down should fail startup (§6.3, §9.6(g)).

**Branch + commit**:
```bash
git checkout -b feat/<service>/CC-6/<ID>
# edit application.yml; git rm application.properties
git commit -m "feat(<service>): CC-6 migrate config to application.yml (<ID>)"
git push -u origin feat/<service>/CC-6/<ID>
```

**MS2 test scenarios (§9.6)**:
- (a) `application.yml` exists, `application.properties` does not.
- (b) `spring.datasource.url` points to `postgres:5432`.
- (c) `spring.data.mongodb.uri`, `spring.data.redis.host`, `jwt.secret`, `jwt.expiration` all present.
- (d-f) Service-specific URIs present.
- (g) Service boots with all NoSQL containers stopped (only PG up).

**Additional integration test** (per service):
```bash
test -f src/main/resources/application.yml
! test -f src/main/resources/application.properties
yq '.spring.datasource.url' src/main/resources/application.yml | grep -q 'postgres:5432'
yq '.jwt.secret' src/main/resources/application.yml | grep -E '.{44,}'
mvn -q spring-boot:run -Dspring-boot.run.profiles=test &
PID=$!; sleep 30
curl -fsS http://localhost:8080/actuator/health | grep -q UP
kill $PID
```

**Verification loop**: run the script + `mvn test` smoke-test. Iterate on yml until all M1 endpoints still respond (caching/auth not yet required at this phase — JWT lands in Phase 4). Soft-dep test: `docker compose stop mongo redis elasticsearch neo4j cassandra && mvn spring-boot:run` must boot.

---

### 2C — `feat/<service>/MOD-0/<ID>` Maven dependencies (×5)

Often bundled with 2B in the same PR — name the branch `feat/<service>/MOD-0/<ID>` if you split, otherwise fold into the CC-6 branch.

**Dependencies**: None for the edit; depends on 2A only when the service first runs.

**Files modified**:
- `pom.xml` (root parent — verify Spring Boot 4.0.3, JDK 25, Hibernate 7.2 already pinned from M1)
- `<service>/pom.xml` (each service)

**Required dependencies per service** (add inside `<dependencies>`):

All five services:
```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-cache</artifactId></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
```

Provider only: `spring-boot-starter-data-elasticsearch`.
Booking only: `spring-boot-starter-data-neo4j`.
Calendar only: `spring-boot-starter-data-cassandra`.

**Gotchas**:
- The dual-Jackson pattern from §1: Spring Boot 4 ships Jackson 3 (`tools.jackson.*`); Hibernate 7.2 uses Jackson 2 (`com.fasterxml.*`) for JSONB. Both are already on the classpath from M1 — do NOT remove `jackson-databind` (group `com.fasterxml.jackson.core`).
- Adding `spring-boot-starter-security` immediately enables a default password on every endpoint. Until Phase 3 lands a proper `SecurityConfig`, every M1 endpoint will start returning 401 with the auto-generated password printed in the boot log. **Mitigation**: in this phase, also add a stub `SecurityConfig` that calls `.permitAll()` on everything; Phase 3 replaces it. Without this stub the rest of M1 is untestable between merges.
- JJWT `0.12.x` has a different API than `0.11.x`. Use `Jwts.builder().subject(...).claim("uid", ...).signWith(key).compact()` and `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`. Do **not** copy older snippets from Stack Overflow.
- Spring Cache + Redis: also add a `RedisCacheConfiguration` bean in Phase 9; just adding the starter does not enable Redis as the cache provider. Default is in-memory `ConcurrentMapCacheManager` until you set `spring.cache.type: redis` in yml. Do that in Phase 9, not here.

**Branch + commit**:
```bash
git checkout -b feat/<service>/MOD-0/<ID>
# edit pom.xml(s) + add stub SecurityConfig
git commit -m "feat(<service>): MOD-0 add security/jwt/mongo/redis starters (<ID>)"
git push -u origin feat/<service>/MOD-0/<ID>
```

**Verification loop**: `mvn -pl <service> -am clean install -DskipTests` must succeed. `mvn dependency:tree | grep -E 'security|jjwt|data-(mongodb|redis|cassandra|neo4j|elasticsearch)'` lists the expected entries.

---

### 2D — Smoke verification gate

After 2A, 2B, 2C are all merged into `main`, run:

```bash
docker compose down -v && docker compose up -d
sleep 120
docker compose ps     # all 6 DBs healthy
for svc in user-service provider-service booking-service calendar-service invoice-service; do
  (cd $svc && mvn -q spring-boot:run &)
done
sleep 60
for port in 8081 8082 8083 8084 8085; do
  curl -fsS http://localhost:$port/actuator/health | grep -q UP || echo "FAIL on $port"
done
```

If anything fails, **block all subsequent phases** until fixed. Phases 3+ assume a working stack.

---

## Phase 3 — Authentication core (canonical classes + per-service security)

This phase introduces the GoF Singleton (DP-5) and the Chain of Responsibility (DP-3), plus the Spring Security plumbing every service needs. Per the team's option-1 decision, **shared classes are copy-pasted verbatim into every service** — there is no `commons` module.

### 3A — `feat/cc/DP-5/<ID>` JwtConfigurationManager (Singleton) + JwtService

Canonical author writes in `user-service`. Phase 3E copies verbatim into the other 4.

**Dependencies**: 2D gate.

**Files created**:
- `user-service/src/main/java/com/team28/booking/user/auth/JwtConfigurationManager.java`
- `user-service/src/main/java/com/team28/booking/user/auth/JwtService.java`

**JwtConfigurationManager contract** (every test in §3.6 grades these specifically):

```java
package com.team28.booking.user.auth;

public final class JwtConfigurationManager {
    private static volatile JwtConfigurationManager instance;
    private final String secret;
    private final long expirationMs;

    private JwtConfigurationManager() {
        // recommended: env-var-with-fallback (§3.6 "Loading config in a non-Spring class")
        String envSecret = System.getenv("JWT_SECRET");
        this.secret = (envSecret != null && !envSecret.isBlank())
            ? envSecret
            : "fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=";   // 44-char Base64 fallback
        String envExp = System.getenv("JWT_EXPIRATION_MS");
        this.expirationMs = (envExp != null) ? Long.parseLong(envExp) : 86_400_000L;
    }

    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) instance = new JwtConfigurationManager();
            }
        }
        return instance;
    }

    public String getSecret()         { return secret; }
    public long   getExpirationMs()   { return expirationMs; }
}
```

**Hard rules — graded by reflection (§3.6)**:
- Class is `final` and **has exactly one declared constructor**, with `private` access. Do NOT add overloads.
- `getInstance()` is `public static`, returns the class type.
- Class is **NOT** annotated with `@Component`, `@Service`, `@Configuration`, `@Bean`, or any Spring stereotype. The reflection test fails on any of those.
- Two consecutive `getInstance()` calls return the same reference (`==`, not `.equals`).
- 10 parallel threads calling `getInstance()` must all return the same reference.

**JwtService contract** (§5.2):

```java
@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService() {
        JwtConfigurationManager cfg = JwtConfigurationManager.getInstance();   // NOT @Autowired
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(cfg.getSecret()));
        this.expirationMs = cfg.getExpirationMs();
    }

    public String issue(String email, Long uid, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(email)
            .claim("uid", uid)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirationMs() { return expirationMs; }
}
```

**Hard rules**:
- HS256 algorithm exactly. JJWT enforces ≥32-byte (256-bit) decoded key — anything shorter throws `WeakKeyException` at construction. The fallback secret is 32 bytes when Base64-decoded.
- Token payload must contain `sub` (email), `uid` (Long), `role` (string `CLIENT`/`ADMIN`), `iat`, `exp`. The `uid` claim is what S1-F12 / S3-F12 ownership checks read — getting the field name wrong breaks those features.

**Gotchas**:
- `Jwts.SIG.HS256` exists in JJWT 0.12+; older `SignatureAlgorithm.HS256` does not compile against the new API.
- Claims values come back as `Object`; cast `uid` carefully — Jackson deserializes JSON numbers as `Integer` if small. Use `((Number) claims.get("uid")).longValue()`.

**Branch + commit**:
```bash
git checkout -b feat/cc/DP-5/<ID>
git commit -m "feat(cc): DP-5 introduce JwtConfigurationManager singleton and JwtService (<ID>)"
```

**Tests** (write `JwtConfigurationManagerTest.java`):
```java
// §3.6 (a) (b) — single private constructor + public static getInstance()
Constructor<?>[] ctors = JwtConfigurationManager.class.getDeclaredConstructors();
assertEquals(1, ctors.length);
assertTrue(Modifier.isPrivate(ctors[0].getModifiers()));
Method m = JwtConfigurationManager.class.getDeclaredMethod("getInstance");
assertTrue(Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()));

// §3.6 (c) — reference equality
assertSame(JwtConfigurationManager.getInstance(), JwtConfigurationManager.getInstance());

// §3.6 (d) — 10-thread race
ExecutorService es = Executors.newFixedThreadPool(10);
List<Future<JwtConfigurationManager>> fs = IntStream.range(0, 10)
    .mapToObj(i -> es.submit(JwtConfigurationManager::getInstance)).toList();
JwtConfigurationManager first = fs.get(0).get();
for (var f : fs) assertSame(first, f.get());

// §3.6 (e) — no Spring stereotype
for (var ann : JwtConfigurationManager.class.getAnnotations()) {
    String n = ann.annotationType().getName();
    assertFalse(n.startsWith("org.springframework"));
}
```

**Additional integration test**: instantiate `JwtService` and verify it resolves the secret via the singleton, NOT via `@Value` injection. Use `@SpringBootTest` and assert that `applicationContext.getBean(JwtConfigurationManager.class)` throws `NoSuchBeanDefinitionException` (proves it is not a Spring bean).

---

### 3B — `feat/cc/DP-3/<ID>` Chain of Responsibility (AuthHandler chain) + JwtAuthenticationFilter

**Dependencies**: 3A.

**Files created** (all in `user-service/src/main/java/com/team28/booking/user/auth/handlers/`):
- `AuthContext.java` — record carrying request, raw token, parsed claims, loaded user, required role, error result.
- `AuthHandler.java` — abstract class with `protected AuthHandler next; public AuthHandler setNext(AuthHandler n) { this.next=n; return n; } public abstract void handle(AuthContext ctx)`.
- `TokenExtractionHandler.java` — reads `Authorization: Bearer <token>` header. Sets `ctx.errorStatus = 401` and stops if missing/malformed.
- `SignatureValidationHandler.java` — calls `JwtService.parse(token)`; on `JwtException` sets `ctx.errorStatus = 401`.
- `UserLoaderHandler.java` — looks up `User` from PG by `uid` claim. Missing user under a valid token is treated as 401 per §3.4(e).
- `RoleAuthorizationHandler.java` — compares `ctx.requiredRole` against `claims.get("role")`. 403 on mismatch. If `ctx.requiredRole` is null, passes through.

**Each handler's `handle()`** ends with:
```java
if (ctx.hasError()) return;          // short-circuit — do not propagate
if (next != null) next.handle(ctx);
```

**Hard rules — graded (§3.4)**:
- `AuthHandler` exposes `setNext(AuthHandler)` and `handle(...)`.
- At least 3 concrete subclasses exist.
- The chain runs **inside** `JwtAuthenticationFilter.doFilterInternal()`. Inline duplication in the filter is grader-failed (§3.4(h)).

**JwtAuthenticationFilter** (`user-service/src/main/java/com/team28/booking/user/auth/JwtAuthenticationFilter.java`):

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthHandler chainHead;   // built in constructor

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepo) {
        AuthHandler ext = new TokenExtractionHandler();
        AuthHandler sig = new SignatureValidationHandler(jwtService);
        AuthHandler usr = new UserLoaderHandler(userRepo);
        AuthHandler rol = new RoleAuthorizationHandler();
        ext.setNext(sig).setNext(usr).setNext(rol);
        this.chainHead = ext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain fc)
            throws ServletException, IOException {
        String path = req.getServletPath();
        if (isPublic(path)) { fc.doFilter(req, res); return; }
        AuthContext ctx = new AuthContext(req);
        chainHead.handle(ctx);
        if (ctx.hasError()) {
            res.setStatus(ctx.getErrorStatus());
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(
            ctx.getAuthenticatedUser(), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getRoleClaim())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        fc.doFilter(req, res);
    }

    private boolean isPublic(String p) {
        return p.equals("/api/auth/register") || p.equals("/api/auth/login")
            || p.startsWith("/actuator/health");
    }
}
```

**Gotchas**:
- The filter must extend `OncePerRequestFilter`, not `Filter` or `GenericFilterBean`. Spring runs `Filter` instances multiple times when forwarding internally — repeated parses spike CPU and may corrupt the security context.
- `RoleAuthorizationHandler` is the only handler that needs to know the **endpoint's** required role — it pulls this from a route table or from `@PreAuthorize` resolution. For M2 the only ADMIN-only path is `PUT /api/users/{id}/role`. Encode that as a single `if`.
- `UserLoaderHandler` reads from `UserRepository` — but only the user-service has it on classpath. In the verbatim copies for the other 4 services, the loader instead checks `userId` against the **shared PostgreSQL** users table via plain `JdbcTemplate`. **Recommended**: in non-user services, `UserLoaderHandler` issues a `SELECT id, role FROM users WHERE id = ?` via JdbcTemplate. This avoids replicating the User JPA entity (which would conflict with M1's user-service-only entity convention).

**Branch + commit**:
```bash
git checkout -b feat/cc/DP-3/<ID>
git commit -m "feat(cc): DP-3 introduce AuthHandler chain and JwtAuthenticationFilter (<ID>)"
```

---

### 3C — `feat/<service>/CC-1-security/<ID>` Per-service SecurityConfig + verbatim copies (×5)

Five branches, one per service.

**Dependencies**: 3A and 3B merged into `main`.

Each non-user service copies the `auth/` package (and `handlers/` sub-package) verbatim from user-service into `<service>/src/main/java/com/team28/booking/<service>/auth/`, renaming the package. Make this copy step a **single chore commit** with subject `chore(<service>): verbatim copy of auth package from user-service (<ID>)` so the source provenance is auditable.

**Files created per service**:
- `<service>/src/main/java/com/team28/booking/<service>/config/SecurityConfig.java`

**Contract** (§5.4):
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain chain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
          .csrf(c -> c.disable())
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(a -> a
              .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
              .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
              .requestMatchers(HttpMethod.PUT, "/api/users/*/role").hasRole("ADMIN")
              .anyRequest().authenticated())
          .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {     // user-service ONLY needs this; safe everywhere
        return new BCryptPasswordEncoder();
    }
}
```

**Gotchas**:
- The auth/register and auth/login matchers exist on **every** service even though only user-service implements those endpoints — non-user services never reach the routes, but the matchers protect the integration tests from accidental matches.
- `hasRole("ADMIN")` matches the authority `ROLE_ADMIN` — that prefix is what `JwtAuthenticationFilter` sets above.
- Replace the stub permit-all SecurityConfig from 2C in the same commit.

**Branch + commit**:
```bash
git checkout -b feat/<service>/CC-1-security/<ID>
# verbatim copy of auth/ from user-service + create SecurityConfig
git commit -m "chore(<service>): verbatim copy of auth package from user-service (<ID>)"
git commit -m "feat(<service>): CC-1 enforce JWT on all endpoints (DP-3) (<ID>)"
```

**MS2 test scenarios (§9.1)**:
- (a) Every endpoint without Authorization → 401 (except /api/auth/register, /api/auth/login, /actuator/health).
- (b) Public endpoints → 2xx without token.
- (c) `Bearer abc` → 401.
- (d) Expired token → 401.
- (e) CLIENT calling `PUT /api/users/{id}/role` → 403.
- (f) Booking has exactly 3 public endpoints. **Booking-team enumeration test**:
   ```bash
   grep -rE '@(Get|Post|Put|Delete)Mapping' booking-service/src/main/java | wc -l
   # subtract the 3 public ones; remainder must equal `(401-count seen in scan)`.
   ```

**Additional integration tests**:
- Issue a token via Phase 5; set `JWT_EXPIRATION_MS=2000` for the test profile; wait until expired; re-call → 401.
- Strip every char from a valid token's signature segment → 401.
- Forge a token signed with the wrong secret → 401.
- Construct a token whose `uid` does not exist in PG → 401 (UserLoaderHandler).

---

## Phase 4 — DP infrastructure: Observer + Factory + MongoEvent + Adapters

This phase delivers the design-pattern infrastructure that every M1 retrofit and every M2 feature plugs into.

### 4A — `feat/cc/DP-2/<ID>` Observer skeleton (canonical in user-service, verbatim copied)

**Dependencies**: Phase 3 merged.

**Files created** (`user-service/src/main/java/com/team28/booking/user/observer/`):

```java
public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}

public abstract class Observable {
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    public void register(EntityObserver o)    { observers.add(o); }
    public void unregister(EntityObserver o)  { observers.remove(o); }
    protected void notifyObservers(String eventType, Object payload) {
        for (EntityObserver o : observers) {
            try { o.onEvent(eventType, payload); }
            catch (Exception ex) { /* swallow per §3.3 failure policy */ }
        }
    }
}

@Component
public class MongoEventLogger implements EntityObserver {
    private final EventFactory factory;
    private final MongoEventRepository repo;       // Spring Data Mongo repo for the bound EventType
    private final EventType boundType;             // injected per-service
    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    @Override public void onEvent(String eventType, Object payload) {
        try {
            Map<String,Object> params = new HashMap<>();
            params.put("action", eventType);
            if (payload instanceof Map<?,?> m) params.putAll((Map<String,Object>) m);
            else params.put("payload", payload);
            MongoEvent ev = factory.createEvent(boundType, params);
            repo.save(ev);
        } catch (Exception ex) {
            log.warn("MongoEventLogger failed for {} action={}: {}", boundType, eventType, ex.getMessage());
            // intentional: do NOT rethrow — §3.3 failure policy
        }
    }
}
```

**Hard rules — graded (§3.3)**:
- `EntityObserver` is an **interface** with method `onEvent(String, Object)`.
- `MongoEventLogger` implements `EntityObserver`.
- No method anywhere annotated `@EventListener` writes to MongoDB. The grader does a static scan — if any `@EventListener` method has a `mongoTemplate.save()` or `repo.save(ev)` call where `ev` is any MongoEvent type, the test fails.
- `MongoEventLogger` catches `Exception`, calls `log.warn(...)`, and never rethrows. The PG transaction must commit even if Mongo is down.

**Gotchas**:
- The 5 services each get **their own** `MongoEventLogger` instance bound to a different `EventType` at construction. The user copy binds `AUTH`, provider binds `PROVIDER`, etc. (§4.5 "Composition workflow" step c).
- Subjects (services) need to register the logger with `register()`. Easiest: in each service add a `@Configuration` that does `@PostConstruct register(logger)` against every `Observable` `@Service`. Alternative: inject the logger directly into each service that fires events.

**Branch + commit**: `feat/cc/DP-2/<ID>` with subject `feat(cc): DP-2 introduce Observer skeleton EntityObserver and MongoEventLogger (<ID>)`.

---

### 4B — `feat/cc/DP-6/<ID>` Factory + MongoEvent interface (canonical in invoice-service, verbatim copied)

**Dependencies**: 4A.

**Files created** (`invoice-service/src/main/java/com/team28/booking/invoice/factory/`):

```java
public interface MongoEvent {
    String        getId();
    LocalDateTime getTimestamp();
    String        getAction();
    Map<String,Object> getDetails();
}

public enum EventType { AUTH, PROVIDER, BOOKING, CALENDAR, PAYMENT_AUDIT }

@Component
public class EventFactory {
    public MongoEvent createEvent(EventType type, Map<String,Object> params) {
        String action = (String) params.get("action");
        LocalDateTime ts = LocalDateTime.now();
        switch (type) {
            case AUTH:           return new AuthEvent(action, ts, params);
            case PROVIDER:       return new ProviderEvent(action, ts, params);
            case BOOKING:        return new BookingEvent(action, ts, params);
            case CALENDAR:       return new CalendarEvent(action, ts, params);
            case PAYMENT_AUDIT:  return new PaymentAuditEvent(action, ts, params);
        }
        throw new IllegalArgumentException("unknown event type: " + type);
    }
}
```

**Hard rules — graded (§3.7)**:
- `MongoEvent` interface with **exactly** the 4 methods listed (signatures must match).
- All 5 concrete event classes implement `MongoEvent`.
- `EventFactory` exposes `createEvent(EventType, Map<String,Object>)` returning `MongoEvent`.
- Every `MongoEvent` instance in code is created via `EventFactory.createEvent(...)`. The grader's static scan flags any `new AuthEvent(...)` etc. inside `service` packages.

**Gotchas**:
- The five concrete event classes can NOT all live in the canonical author's service — Spring Data needs the document class on the classpath of the service that saves it. Therefore the canonical EventFactory in invoice-service uses **fully-qualified class names** that exist only after each service copies the right concrete class into its own package. The simplest workable approach: each service has its own copy of `EventFactory` that switches only on its bound `EventType` and throws on others. Mention this in the commit message.
- `LocalDateTime.now()` uses the system zone — make sure the Docker base image is `eclipse-temurin:25.0.2_10-jdk` and the container TZ is UTC (`TZ=UTC` env), so that S5-F11's date-range filter works correctly across services.

---

### 4C — `feat/<service>/DP-2-mongoevent/<ID>` Concrete event class per service (×5)

After 4A and 4B merge.

**Per-service file** (replace `<svc>` with the right tokens):

```java
@Document(collection = "auth_events")    // collection name varies — see below
public class AuthEvent implements MongoEvent {
    @Id  private String id;
    @Indexed  private Long userId;       // service-specific FK column varies
    private String action;
    private LocalDateTime timestamp;
    private Map<String,Object> details;
    // public ctor (action, ts, params), implements interface methods
}
```

| Service | Class | Collection | FK field |
|---|---|---|---|
| user | `AuthEvent` | `auth_events` | `userId: Long` |
| provider | `ProviderEvent` | `provider_events` | `providerId: Long` |
| booking | `BookingEvent` | `booking_events` | `bookingId: Long` |
| calendar | `CalendarEvent` | `calendar_events` | `providerId: Long` |
| invoice | `PaymentAuditEvent` | `payment_audit_trail` | `invoiceId: Long`, plus required-conditional `method: String` and `amount: Double` fields |

**Hard rules — graded (§7.1)**:
- All 5 are **classes**, not records (§7.1 first paragraph).
- Action values use `UPPER_SNAKE_CASE`. Primary values per §7.1.x — extensible (the grader does not enumerate retrofit values).
- `PaymentAuditEvent`'s `method` and `amount` are **required (not null)** for actions in `{CREATED, COMPLETED, FAILED, REFUNDED, REFUND_DENIED, DISCOUNT_APPLIED, RETRY_ATTEMPTED}`. Null only for `ANALYTICS_VIEWED`. S5-F11 silently filters out events missing these fields — do NOT save with method=null on a payment-shaped action.

**Gotchas**:
- Spring Data Mongo auto-creates the collection on first save. To guarantee the right name across environments, set `@Document(collection = "...")` exactly as in the table.
- `@Indexed` on the FK speeds up the per-user / per-provider activity queries used by S1-F12, S5-F11 etc. Skipping this works but degrades feature performance under load — the grader does not check for the index but feature tests may time out.

**Repository per service**: `interface XEventRepository extends MongoRepository<XEvent, String>` with custom queries for the activity-feed paginations needed by features.

---

### 4D — Provider/Booking/Calendar NoSQL entities (3 separate branches)

| Branch | Files |
|---|---|
| `feat/provider/es-doc/<ID>` | `provider-service/src/main/java/com/team28/booking/provider/search/ProviderSearchDocument.java`, `ProviderSearchRepository.java` per §7.2.1 |
| `feat/booking/neo4j-entities/<ID>` | `booking-service/src/main/java/com/team28/booking/booking/neo4j/UserNode.java`, `ProviderNode.java`, `BookedRelationship.java`, repositories per §7.3 |
| `feat/calendar/cassandra-entity/<ID>` | `calendar-service/src/main/java/com/team28/booking/calendar/cassandra/CalendarAvailabilityEvent.java`, repository per §7.4.1 |

**Hard rules**:
- ES `ProviderSearchDocument` field types: `id` Keyword, `name` Text (analyzed), `specialty` Keyword, `pricingTier` Keyword, `description` Text, `rating` Double, `status` Keyword. Annotate fields with `@Field(type = FieldType.X)`.
- Neo4j `BOOKED` relationship direction: `(User)-[:BOOKED]->(Provider)` with `bookingCount: Integer` and `lastBookingDate: LocalDateTime`.
- Cassandra: partition key `provider_id`, clustering `timestamp DESC`. Every query MUST include `provider_id` in WHERE — this is enforced by Cassandra at query-plan time.

**Commit subjects**:
- `feat(provider): introduce ProviderSearchDocument and Elasticsearch repo (<ID>)`
- `feat(booking): introduce UserNode, ProviderNode, and BOOKED relationship (<ID>)`
- `feat(calendar): introduce CalendarAvailabilityEvent Cassandra entity (<ID>)`

---

### 4E — `feat/<service>/DP-7-adapters/<ID>` NoSQL adapter classes (×5)

**Files per service** (in `<service>/.../adapter/`):

| Service | Adapters required |
|---|---|
| user | `MongoDocumentAdapter` |
| provider | `MongoDocumentAdapter`, `ElasticsearchHitAdapter` |
| booking | `MongoDocumentAdapter`, `Neo4jRecordAdapter` |
| calendar | `MongoDocumentAdapter`, `CassandraRowAdapter` |
| invoice | `MongoDocumentAdapter` |

**Contract**: each adapter has a single `adapt(source) → DTO` method (§3.8). No universal "EntityDto" base type.

**Stub** (will be filled out as features land — at this phase only the empty class with `adapt(...)` matters for grading):
```java
@Component
public class MongoDocumentAdapter {
    public <T> T adapt(Document doc, Class<T> targetType) { ... }
}
```

**Verification**:
- Reflection: every adapter class exists in the right service package and exposes a method named `adapt`. The grader iterates the per-service adapter list (§3.8(a)) — missing one fails the entire DP-7 grade.

---

## Phase 5 — M1 modifications: User entity (BCrypt + roles)

### 5A — `feat/user/MOD-1/<ID>` BCrypt password hashing

**Dependencies**: Phase 3 (PasswordEncoder bean), Phase 4 (no — this is independent).

**Files modified**:
- `user-service/.../service/UserService.java` — every place that sets `user.setPassword(...)` is wrapped in `passwordEncoder.encode(...)`.
- `user-service/.../dto/UserDTO.java` — exclude or null the password field. `@JsonIgnore` on the entity field is the simplest path; verify no DTO leaks the value.
- `user-service/.../seed/Seed.java` (or equivalent) — re-encode every seeded password with BCrypt before save.

**Hard rules — graded (§4.1)**:
- Stored password starts with `$2a$`, `$2b$`, or `$2y$` and is exactly 60 chars.
- `GET /api/users/{id}` and **every** other endpoint returning a User must omit the password field. Include S1-F1, S1-F3, etc. — these are M1 endpoints that may have left the field in.
- Re-running the seed mechanism produces hashed passwords for every user (no plaintext leaks).

**Gotchas**:
- `BCryptPasswordEncoder.encode()` produces a different hash on every call (random salt) — never use it inside an `equals`-style comparison; only use `matches(raw, hash)`.
- If existing seed data has plaintext passwords, the **first login** for those users would fail (BCrypt cannot match against plaintext). Two paths: (a) re-seed (drop+reload), (b) detect plaintext on first login (length != 60 or doesn't start with `$2`) and re-encode in place. The spec accepts either (§4.1 second bullet "or hash them on first login"). Pick (a) for simplicity.
- The grader queries the `users` table directly — make sure your `seed` runs at boot (`@PostConstruct` or `CommandLineRunner`) so the columns are populated by the time tests start.

**Branch + commit**: `feat/user/MOD-1/<ID>` with subject `feat(user): MOD-1 hash passwords with BCrypt and hide field in DTO (<ID>)`.

**MS2 test scenarios (§4.1)**:
- (a) Register a user with `securePassword123` → password column matches `^\$2[aby]\$.{56}$`.
- (b) Length is exactly 60.
- (c) Stored value ≠ `securePassword123`.
- (d) Login with same credentials → 200.
- (e) `GET /api/users/{id}` JSON has no `password` key (or it is `null`).
- (f) Re-seed → every row's password is BCrypt.

**Additional integration tests**:
- Login with the right email but a wrong password → 401 (proves matches() is being called, not naive `equals`).
- Two consecutive registrations with the same plaintext → different hashes (proves random salt).
- Read every M1 GET endpoint that returns a user object (S1-F1 search, S1-F3 summary, S1-F8 profile, every CRUD GET) and grep the response for `"password"` — must be absent in all.

---

### 5B — `feat/user/MOD-2/<ID>` Role values + ADMIN seed

**Files modified**:
- `user-service/.../model/User.java` — verify `Role` enum has `CLIENT, ADMIN` (no rename, no removal).
- `user-service/.../seed/Seed.java` — ensure at least one row inserted with `role = ADMIN`.
- `user-service/.../service/UserService.java` (`register` method) — explicitly `user.setRole(CLIENT)` regardless of request body. The bad-faith `"role": "ADMIN"` in register body is silently ignored (§4.2(e)).

**Hard rules — graded (§4.2)**:
- PG enum type still contains both `CLIENT` and `ADMIN` (`SELECT enumlabel FROM pg_enum WHERE enumtypid = 'role'::regtype`).
- Default role on register = `CLIENT`.
- `"role":"ADMIN"` in register body is ignored (server returns CLIENT).
- ADMIN-only endpoint exists (CC-2, Phase 10). Until that lands, the role enum and seed are the only artifact graded here.

**MS2 test scenarios (§4.2)**: a–h. Tests (e), (f), (g), (h) require Phase 10 (CC-2) to be merged.

---

## Phase 6 — M1 modifications: JSONB additive keys

### 6A — `feat/provider/MOD-3/<ID>` Provider.serviceDetails.description default

**Dependencies**: Phase 2.

**Files modified**:
- `provider-service/.../service/ProviderService.java` — on every `create` and `update` of a Provider, ensure `serviceDetails` map includes a `description` key (default empty string `""`).
- `provider-service/.../seed/Seed.java` — every seeded Provider row carries `serviceDetails.description = "<seed text>"` (or empty).

**Reader contract**: any feature that reads `description` (S2-F11 first, then S2-F10 indexer + dashboard) must default to `""` when the key is missing or null. Add a static helper:
```java
public static String descriptionOrEmpty(Map<String,Object> serviceDetails) {
    return Optional.ofNullable(serviceDetails)
        .map(m -> m.get("description")).map(Object::toString).orElse("");
}
```

**Branch + commit**: `feat/provider/MOD-3/<ID>` with subject `feat(provider): MOD-3 add additive description key to serviceDetails (<ID>)`.

---

### 6B — `feat/invoice/MOD-4/<ID>` Invoice.transactionDetails.cancellationFee=0 in S5-F4

**Files modified**:
- `invoice-service/.../service/InvoiceService.java` — inside the M1 S5-F4 (`POST /api/invoices/booking/{bookingId}` "Process Invoice for Booking") method, when constructing the JSONB map, add `transactionDetails.put("cancellationFee", 0)`.

**Reader contract** (§4.6 fallback): any aggregate reader (S5-F10) treats missing/null `cancellationFee` as 0. No backfill migration.

**Tests** (§4.6, §10.5.1):
- Create new invoice → `transactionDetails->>'cancellationFee' = '0'` in PG.
- Read older M1 invoice that lacks the key → S5-F10 still returns sensible totals (Phase 11 verification).

---

## Phase 7 — M1 modifications: Observer wiring + invoice audit trail + Provider auto-index

This is the **largest M1 modification phase** by line count and the riskiest by merge-conflict surface (every service's M1 controllers/services get edited). Sequence the MOD branches per service in the order below; do not parallelize within a service.

### 7A — `feat/<service>/MOD-5-observer/<ID>` Observer wiring on all M1 writes (×5)

**Dependencies**: Phase 4.

**For each service**: every M1 write endpoint (the 18 feature writes + 30 CRUD writes per §4.4.4) needs to call `notifyObservers(actionString, payload)` after the PG transaction commits. Service-by-service action enumeration:

| Service | Feature actions to emit | CRUD actions to emit |
|---|---|---|
| user | `USER_UPDATED` (S1-F2), `USER_DEACTIVATED` (S1-F4), `DEFAULT_ADDRESS_SET` (S1-F7) | `USER_CREATED`, `USER_DELETED` |
| provider | `SERVICE_DETAILS_UPDATED` (S2-F2), `AVAILABILITY_TOGGLED` (S2-F4), `RATING_RECORDED` (S2-F7), `CERTIFICATION_VERIFIED` (S2-F8) | `PROVIDER_CREATED`, `PROVIDER_DELETED` |
| booking | `PROVIDER_ASSIGNED` (S3-F2), `BOOKING_COMPLETED` (S3-F4), `BOOKING_CANCELLED` (S3-F7), `SERVICES_ADDED` (S3-F8) | `BOOKING_CREATED`, `BOOKING_DELETED` |
| calendar | `SLOT_CREATED` (S4-F2), `BATCH_SLOTS_CREATED` (S4-F4), `OLD_SLOTS_PURGED` (S4-F7) | `TIME_SLOT_DELETED` (and `TIME_SLOT_CREATED`/`UPDATED`) |
| invoice | `REFUNDED` (S5-F2), `DISCOUNT_APPLIED` (S5-F5), `RETRY_ATTEMPTED` (S5-F7) | `INVOICE_DELETED` |

**Hard rules**:
- All emission goes through the chain: service → `notifyObservers(action, payload)` → `MongoEventLogger.onEvent` → `EventFactory.createEvent` → repo.save.
- **Never** instantiate `new XxxEvent(...)` directly in service code (§3.7 test (h)).
- **Never** annotate any method `@EventListener` to write Mongo (§3.3).
- The Postgres transaction must NOT roll back on Mongo failure (§3.3 failure policy). MongoEventLogger swallows the exception.

**Gotchas**:
- Emission timing: emit **after** `transactionTemplate.execute(...)` returns so a successful Mongo write is not paired with a rolled-back PG row. Easiest pattern: emit at the end of the `@Transactional` method body — Mongo write happens before TX commit, but failure is caught & swallowed. Best-of-both: use `TransactionSynchronizationManager.registerSynchronization(...)` to fire on `afterCommit`.
- Provider rating: every M1 S2-F7 rating recording emits `RATING_RECORDED` AND triggers cache invalidation of `provider-service::S2-F12::{providerId}` per §4.4.4. Wire both in this phase.

**Branch + commit**: `feat/<service>/MOD-5-observer/<ID>` with subject `feat(<service>): MOD-5 wire Observer on M1 writes (DP-2) (<ID>)`.

**Tests (§3.3, §4.5)**:
- Trigger every write listed above; assert exactly one event document appears in the right Mongo collection.
- Drop the Mongo container; trigger any write; PG row commits successfully; service log includes a WARN line; no 500 to client.
- Unregister all observers (test-only hook); trigger a write; no Mongo doc appears (proves the write goes through the observer chain, not direct calls).

---

### 7B — `feat/invoice/MOD-6/<ID>` Invoice audit-event emission (S5-F4 + S5-F2 + simulateFailure)

**Dependencies**: 7A invoice branch merged first.

**Files modified**:
- `invoice-service/.../service/InvoiceService.java`:
  - In M1 S5-F4 `processInvoiceForBooking`: emit `CREATED` after row insert, `COMPLETED` after status transition.
  - In M1 S5-F2 `processRefund`: emit `REFUNDED`.
- `invoice-service/.../controller/InvoiceController.java`:
  - Accept optional `?simulateFailure=true` query parameter on M1 S5-F4. When true, short-circuit: set `Invoice.status = FAILED` in PG, emit `FAILED` audit event with `method` + `amount` from request, return 200.

**Hard rules — graded (§4.5 "Invoice Service payment_audit_trail")**:
- `CREATED` includes `method` + `amount` matching the new Invoice row.
- `COMPLETED` written when the status flips to COMPLETED (M1 S5-F4 ends with this status by default).
- `FAILED` written ONLY on the simulateFailure path; payload includes `method`, `amount`, plus `details.reason = "simulated_gateway_failure"`.
- `REFUNDED` written on M1 S5-F2 success with `method`, `amount` (refund amount), `details.refundReason`.

**Gotchas**:
- The `?simulateFailure=true` param is required for S5-F11 test data — without it, test scenario (a) of S5-F11 cannot produce failure rows. Make sure the param is wired in the controller signature, not silently ignored.
- Action names UPPER_SNAKE_CASE — the grader is case-sensitive on `CREATED`, not `Created`.

**Branch + commit**: `feat/invoice/MOD-6/<ID>` subject `feat(invoice): MOD-6 emit CREATED/COMPLETED/FAILED/REFUNDED audit events (DP-2) (<ID>)`.

**MS2 tests (§4.5 (e), (f) + §10.5.1 cross-checks)**:
- Call M1 S5-F4 → 2 events (CREATED + COMPLETED) in `payment_audit_trail`.
- Call M1 S5-F4 with `?simulateFailure=true` → 1 event (FAILED), Invoice row in PG has `status = FAILED`.
- Call M1 S5-F2 → 1 REFUNDED event.

---

### 7C — `feat/provider/MOD-7-autoindex/<ID>` Provider CRUD auto-index to Elasticsearch

**Dependencies**: Phase 4D ES doc, 7A provider branch.

**Files**:
- `provider-service/.../listener/ProviderEntityListener.java` — `@PostPersist`, `@PostUpdate`, `@PostRemove` on the Provider JPA entity.
- OR `provider-service/.../service/ProviderService.java` — call an `IndexingService` after every CRUD method.
- `provider-service/.../service/IndexingService.java` — wraps Elasticsearch repository upsert + delete, plus emits the right MongoEvent.

**Hard rules — graded (§4.5 "Provider CRUD auto-index" + §10.2.2 step f)**:
- `POST /api/providers` → re-index ES doc (effect identical to S2-F11). Emit `INDEXED` with `details.source = "auto_crud_create"`.
- `PUT /api/providers/{id}` → re-index. Emit `INDEXED` with `details.source = "auto_crud_update"`.
- `DELETE /api/providers/{id}` → remove ES doc. Emit `PROVIDER_DELETED` (NOT `INDEXED`).
- Implementation must NOT inline the ES call in every CRUD controller method — use the listener or a service-level hook.
- After every auto-index write, invalidate `provider-service::S2-F10::*` (cache layer comes in Phase 9 — wire the call now; it becomes a no-op until Phase 9 lands).

**Gotchas**:
- JPA entity listener requires no Spring DI; if you use `@PostPersist` you cannot `@Autowired` services into the listener. Either: (a) static accessor for an ES repo bean, (b) use Spring Data REST event listener, or (c) skip the JPA listener and put the call in `ProviderService` after every CRUD method.
- Recommended: option (c) — put the auto-index call inside `ProviderService.create/update/delete` so DI is trivial.
- ES auto-create on first save: if the index `providers` does not exist yet, Spring Data ES creates it with a default mapping. To control mapping (Keyword vs Text), bootstrap the index at startup with `IndicesClient.create(...)` or annotate the document class fields with `@Field(type = FieldType.Keyword)` / `@Field(type = FieldType.Text)`.

**Branch + commit**: `feat/provider/MOD-7-autoindex/<ID>` subject `feat(provider): MOD-7 auto-index providers to Elasticsearch on CRUD (DP-2) (<ID>)`.

**Tests (§4.5 (g), §10.2.2 (d))**:
- Create provider via CRUD without calling `/index` → search for it via S2-F10 (Phase 12) returns it.
- Update provider's name via CRUD → search by new name returns it.
- Delete provider via CRUD → search returns no match for that id.

---

## Phase 8 — Builder + Adapter retrofits

### 8A — `feat/<service>/MOD-8-builder/<ID>` Builder retrofit (×5)

**Per-service M1 DTOs to retrofit** (only DTO-returning features with 5+ fields per §3.5):
- user: S1-F3 (`UserBookingSummaryDTO`), S1-F6 (`TopClientDTO`), S1-F8 (`UserProfileWithAddressesDTO`), S1-F9 (`UserLanguagePreferenceDTO`).
- provider: S2-F3 (`ProviderEarningsDTO`), S2-F6 (`TopRatedProviderDTO`), S2-F9 (`ProviderExpiredCertDTO`). NOT S2-F8 — returns entity.
- booking: S3-F3 (`BookingPriceEstimateDTO`), S3-F6 (`BookingAnalyticsDTO`), S3-F9 (`BookingDetailsWithServicesDTO`). NOT S3-F8 — returns entity.
- calendar: S4-F3 (`AvailableProviderDTO`), S4-F6 (`TimeSlotRangeDTO`), S4-F8 (`ProviderUtilizationDTO`), S4-F9 (`IdleProviderDTO`).
- invoice: S5-F3 (`UserInvoiceSummaryDTO`), S5-F6 (`RevenueReportDTO`), S5-F8 (`InvoiceWithDiscountsDTO`), S5-F9 (`MostUsedDiscountDTO`).

**Two paths** (§3.5):
- Convert the record to a class with a static inner `Builder`.
- Keep the record and add an external `<DtoName>Builder` class whose `build()` calls the canonical constructor.

**Hard rules — graded (§3.5)**:
- Every DTO has `static builder()` returning a Builder.
- Chained setters return the Builder type (`this`).
- `build()` returns the DTO type.
- The service constructs the DTO via the Builder, **not** via direct constructor invocation. The grader source-scans for `new XxxDTO(` outside the Builder.

**Gotchas**:
- Records: external Builder is the safer path — keeps the record contract intact and avoids bytecode-level surprises in the canonical constructor. Use `record UserBookingSummaryDTO(...) {}` + `class UserBookingSummaryDTOBuilder { ... }`.
- If the grader reflects on `builder()` it expects the **DTO class** to expose it — for the external-builder pattern, add a static factory method on the record:
  ```java
  public record UserBookingSummaryDTO(...) {
      public static UserBookingSummaryDTOBuilder builder() { return new UserBookingSummaryDTOBuilder(); }
  }
  ```

**Branch + commit**: `feat/<service>/MOD-8-builder/<ID>` subject `feat(<service>): MOD-8 retrofit Builder on M1 DTOs (DP-4) (<ID>)`.

**Tests (§3.5 a-e)**: reflective check + integration call to each retrofit feature.

---

### 8B — `feat/<service>/MOD-9-adapter/<ID>` Object[] Adapter retrofit (×5)

Conditional: skip the branch entirely if the team chose JPQL constructor expressions / DTO projections in M1.

**Mandatory case** — `S1-F3` (the only feature M1 explicitly mandates `Object[]`).

**For each service**: scan service code for `Object[]` usages mapped inline to a DTO. Wrap each in an `ObjectArrayDtoAdapter` class with a single `adapt(Object[]) → DTO` method.

**Hard rules — graded (§3.8 (e), (f))**:
- For S1-F3: an `ObjectArrayDtoAdapter` (or similarly-named) class exists and is the only path that maps `Object[]` to `UserBookingSummaryDTO`. Inline mapping in the service is grader-failed.
- For other features that used `Object[]`: each has a corresponding adapter. Features using JPQL/DTO projection are exempt.

**Branch + commit**: `feat/<service>/MOD-9-adapter/<ID>` subject `feat(<service>): MOD-9 retrofit Adapter on Object[] M1 mappings (DP-7) (<ID>)`.

---

## Phase 9 — Redis caching retrofit + invalidation

### 9A — `feat/<service>/CC-3-cache/<ID>` Cache infrastructure per service (×5)

**Dependencies**: Phase 2 (Redis up + spring-boot-starter-cache + spring-boot-starter-data-redis on classpath).

**Files**:
- `<service>/.../config/CacheConfig.java`:
  ```java
  @Configuration
  @EnableCaching
  public class CacheConfig {
      @Bean
      public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
          RedisCacheConfiguration baseline = RedisCacheConfiguration.defaultCacheConfig()
              .computePrefixWith(name -> name)                            // we control the prefix
              .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
              .disableCachingNullValues();
          Map<String, RedisCacheConfiguration> perCache = Map.of(
              "<service>::S1-F1", baseline.entryTtl(Duration.ofMinutes(5)),
              "<service>::S1-F3", baseline.entryTtl(Duration.ofMinutes(10)),
              // ... per the TTL table below
              "<service>::user", baseline.entryTtl(Duration.ofMinutes(15))
          );
          return RedisCacheManager.builder(cf)
              .cacheDefaults(baseline.entryTtl(Duration.ofMinutes(10)))
              .withInitialCacheConfigurations(perCache)
              .build();
      }
  }
  ```
- yml addition: `spring.cache.type: redis` and `spring.cache.redis.use-key-prefix: false`.

**TTL table by feature ID**:
| Feature | TTL | Notes |
|---|---|---|
| F1 | 5 min | search |
| F3 | 10 min | DTO; for S3-F3 (POST estimate), cache by request-body hash, 5 min |
| F5 | 5 min | JSONB query |
| F6 | 10 min | report |
| F8 | 15 min | relationship DTO |
| F9 | 10 min | combined |
| Entity GET-by-ID | 15 min | one per entity |

**Cache key contract (§4.4.5)**:
- Entity detail: `<service>::<entity>::<id>` — example `user-service::user::42`.
- Feature result: `<service>::<featureId>::<param-hash>` — example `user-service::S1-F3::42`.

**Gotchas**:
- Spring's `@Cacheable("user-service::user")` would normally produce `user-service::user::42` with the default key generator — but the default key generator stringifies parameters, not always cleanly. Use an explicit `key = "#id"` SpEL expression on every annotation.
- For S3-F3 (POST estimate), Spring's default key generator hashes Java objects — the same JSON body posted twice may not produce the same key if the DTO has timestamps. Use an explicit `key = "T(java.util.Objects).hash(#req.providerId, #req.appointmentDate, #req.services)"`.
- `disableCachingNullValues()` is critical: caching a 404-shaped null degrades to a stuck-empty cache.
- Soft dependency: Redis being down must NOT crash a `@Cacheable` call. By default Spring Cache propagates the connection exception. Wrap with `RedisCacheManager.builder().disableCreateOnMissingCache().enableStatistics()` plus a `@Bean CacheErrorHandler` that logs WARN and returns null on `handleCacheGetError`. Without this, the soft-dependency test (§4.4.6 test (g)) fails.

---

### 9B — same branch — `@Cacheable` annotations on M1 reads

**Booking-platform-wide enumeration** (§4.4.1, §4.4.2, §4.4.3 — 27 + 10 = 37 endpoints):

User: F1, F3, F5, F6, F8, F9 + 2 CRUD GET-by-ID (user, saved-address).
Provider: F1, F3, F5, F6, F9 + 2 CRUD GET-by-ID (provider, provider-certification).
Booking: F1, F3 (POST estimate), F5, F6, F9 + 2 CRUD GET-by-ID (booking, booking-service).
Calendar: F1, F3, F5, F6, F8, F9 + 1 CRUD GET-by-ID (time-slot).
Invoice: F1, F3, F6, F8, F9 + 3 CRUD GET-by-ID (invoice, discount, invoice-discount).

Each endpoint gets `@Cacheable(cacheNames = "<service>::S<n>-F<m>", key = "...")` or `@Cacheable(cacheNames = "<service>::<entity>", key = "#id")`.

**Hard rules — graded (§9.3)**:
- After two consecutive identical GETs, Redis contains the expected key.
- Second call latency lower than first.
- List endpoints (`GET /api/<entity>`) produce **NO** cache key.

---

### 9C — same branch — invalidation on writes (§4.4.4)

**M1 write inventory**: 18 feature writes + 30 CRUD writes = 48 invalidating writes (§4.4.4 last paragraph).

For each write, the service must:
1. Delete `<service>::<entity>::{id}` (entity detail).
2. Wildcard-delete `<service>::S{n}-F{m}::*` for every feature whose output may include that entity. Use `redisTemplate.execute(scanCallback)` + DEL.

**Helper utility** (drop into each service's `cache/CacheInvalidator.java`):
```java
@Component
public class CacheInvalidator {
    private final StringRedisTemplate redis;
    public void deleteKey(String key) { redis.delete(key); }
    public void wildcardDelete(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(1000).build();
        try (Cursor<byte[]> c = redis.getConnectionFactory().getConnection().scan(opts)) {
            while (c.hasNext()) keys.add(new String(c.next()));
        }
        if (!keys.isEmpty()) redis.delete(keys);
    }
}
```

**Per-service write → invalidation map**: see §4.4.4 — copy the listed wildcard patterns into each MOD branch's invalidation calls.

**M2-write invalidation** (these matter even though M2 features are out of scope for this plan — the *hooks* are infrastructure):
- CC-2 role change (Phase 10) → `user-service::user::{id}` + `user-service::S1-F12::*`.
- Any Booking write referencing a `providerId` → `provider-service::S2-F12::{providerId}`.
- Any Booking create/update → `booking-service::S3-F10::*`.
- Any TimeSlot create/update → `calendar-service::S4-F10::*`.
- Any Invoice create/update → `invoice-service::S5-F10::*` + `invoice-service::S5-F11::*`.

**NoSQL-writer invalidation** (also infrastructure — wire now even if the M2 features that read these caches don't exist yet):
- S4-F11 → `calendar-service::S4-F12::{providerId}` + `calendar-service::S4-F10::*`.
- S3-F11 → `booking-service::S3-F12::*` (wildcard).
- S2-F11 + Provider auto-index → `provider-service::S2-F10::*`.
- Observer write of data-mutating action to `payment_audit_trail` → `invoice-service::S5-F10::*` + `invoice-service::S5-F11::*`. Filter out `ANALYTICS_VIEWED` and `DASHBOARD_VIEWED` (§4.4.4 last bullet — would self-defeat the cache).

**Gotchas**:
- `redisTemplate.keys(pattern)` is O(N) and blocks Redis. Use SCAN (above helper) — graceful for large keyspaces.
- Over-invalidation is acceptable (§4.4.6) — correctness beats cache-hit ratio.
- The Observer-driven invalidation needs a hook in `MongoEventLogger`: after a successful Mongo save, if the action is in the data-mutating set, fire the corresponding wildcard delete. Match on `action` field BEFORE invalidating, exclude `ANALYTICS_VIEWED`/`DASHBOARD_VIEWED`.

**Tests (§4.4.6, §9.3)**:
- For each cached endpoint: call twice, assert key exists between calls.
- For each write: trigger, assert the affected detail key is deleted from Redis.
- For each wildcard rule: pre-cache 3 keys matching the pattern, trigger the write, assert all 3 are gone.
- Stop Redis container; cached endpoints still return correct data from PG (§4.4.6 (g)).
- TTL: fetch S1-F1 (5 min TTL), wait 5+ min, fetch again — recomputed.

---

## Phase 10 — CC-2 Role management endpoint

### 10 — `feat/cc/CC-2/<ID>` PUT /api/users/{id}/role

**Dependencies**: Phase 5 (roles), Phase 4 (Observer), Phase 9 (cache).

**Files**:
- `user-service/.../controller/UserController.java` — new method `updateRole`.
- `user-service/.../service/UserService.java` — `changeRole(Long id, Role newRole)`.
- `user-service/.../dto/UpdateRoleRequest.java` — single field `role` (string).

**Behavior** (§9.2):
1. JWT validated by filter chain; `RoleAuthorizationHandler` gates on `ADMIN` (route is `PUT /api/users/*/role` per §9.2 + SecurityConfig 3C).
2. Find user by `id` → 404 if absent.
3. Validate `request.role` is a valid `Role` enum value → 400 on `"BANANA"`.
4. Update + save.
5. `notifyObservers("ROLE_CHANGED", Map.of("userId", id, "oldRole", oldRole, "newRole", newRole))`. Observer chain writes the AuthEvent to Mongo, AND triggers cache invalidation per §4.4.4 (which fires `user-service::S1-F12::*` wildcard deletion).
6. Explicitly invalidate `user-service::user::{id}` (entity detail).
7. Return updated user (200).

**Hard rules — graded (§9.2)**:
- ADMIN-only.
- 404 for missing user.
- 400 for invalid role string.
- 401 for missing token.
- 403 for non-ADMIN with valid token.
- ROLE_CHANGED event in `auth_events` with `details.oldRole` and `details.newRole`.
- Cache invalidations as above.

**Token-staleness accepted limitation**: the demoted/promoted user's existing JWT keeps its old role until 24h expiry. Document this in the controller comment so reviewers don't re-introduce a token-revocation list (§9.2 "Token staleness").

**Branch + commit**: `feat/cc/CC-2/<ID>` subject `feat(cc): CC-2 role management endpoint (<ID>)`.

**MS2 tests (§9.2 a-i)**:
All 9 scenarios. Plus integration tests:
- After ROLE_CHANGED, fetch `GET /api/users/{id}/activity` (S1-F12 lands in feature phase, but if it exists for testing) — first event is ROLE_CHANGED.
- After ROLE_CHANGED, `GET /api/users/{id}` — Redis miss (proves cache was invalidated).

---

## Phase 11 — Final integration verification (no new code)

After Phases 1–10 are merged, run the full integration suite **before any of the 15 features start**. Block all feature work until this gate passes.

### 11.1 Hard regression: M1 must still work

Run every M1 manual curl test from M1 spec under the new auth + cache + observer regime:

```bash
# Get a JWT once
TOKEN=$(curl -s -X POST localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Reg","email":"reg@x.com","password":"pw12345678","phone":"+201000000000"}' \
  | jq -r .token)

# Run all 21 booking-service Phase A tests with -H "Authorization: Bearer $TOKEN"
# Run all booking-service Phase B (S3-F3) tests
# Run all booking-service Phase C (S3-F4) tests
# Run all booking-service Phase D (S3-F5) tests
# Mirror for the other 4 services' M1 features
```

All must pass.

### 11.2 Soft regression: NoSQL down

```bash
docker compose stop mongo redis elasticsearch neo4j cassandra
sleep 5
# All 5 services must still respond to M1 GETs (Redis-down test)
# Writes to PG must still commit (Mongo-down test) — Observer logs WARN, no rollback
docker compose start mongo redis elasticsearch neo4j cassandra
```

### 11.3 Pattern reflection sweep

Run a single Java test in each service that loads:
- Singleton (private ctor + getInstance + no Spring annotation) — `JwtConfigurationManagerTest`.
- Observer (`EntityObserver` interface + `MongoEventLogger` impl) — `ObserverContractTest`.
- Chain (`AuthHandler` + setNext + 4 concrete handlers) — `AuthHandlerChainTest`.
- Factory (`MongoEvent` interface + `EventFactory.createEvent` + 5 concrete classes) — `EventFactoryTest`.
- Builder (every M1 retrofit DTO has builder()) — `BuilderRetrofitTest`.
- Adapter (each NoSQL adapter exists; `ObjectArrayDtoAdapter` exists for S1-F3) — `AdapterRetrofitTest`.

Strategy is **NOT** present in M1 code:
```bash
grep -rE 'RefundStrategy|class.*Strategy ' user-service provider-service booking-service calendar-service
# any hit outside invoice-service/src/.../s5f12/ → grader fail
```

### 11.4 Cross-cutting gate

```bash
# CC-1: every endpoint requires JWT except 3
./scripts/scan-public-endpoints.sh    # produces a table; assert booking has exactly 3
# CC-2: role management endpoint exists and works
curl -X PUT -H "Authorization: Bearer $ADMIN" localhost:8081/api/users/2/role -d '{"role":"ADMIN"}'
# CC-3: caching live
redis-cli -a redispass --scan --pattern '*::*::*' | wc -l   # > 0 after running M1 reads
# CC-4: pattern tests above
# CC-5: docker compose with 6 DBs, all healthy
docker compose ps --format '{{.Name}} {{.Health}}'
# CC-6: each service has application.yml not properties
for s in user provider booking calendar invoice; do
    test -f "${s}-service/src/main/resources/application.yml"
    ! test -f "${s}-service/src/main/resources/application.properties"
done
```

If all green, **the team can start the 15 M2 features**. Open a tracking issue titled "Phase 11 gate: PASSED on YYYY-MM-DD" and pin it.

---

## Phase-ordered branch list (copy-paste, swap `<ID>`)

The exhaustive enumeration of branches in dependency order. Any agent picks any branch; the team leader assigns owners separately.

| # | Branch | Lead commit subject |
|---|---|---|
| 1 | `feat/infra/CC-5/<ID>` | `feat(infra): CC-5 add 6-database stack with memory caps and healthchecks (<ID>)` |
| 2 | `feat/user/CC-6/<ID>` | `feat(user): CC-6 migrate config to application.yml (<ID>)` |
| 3 | `feat/provider/CC-6/<ID>` | `feat(provider): CC-6 migrate config to application.yml (<ID>)` |
| 4 | `feat/booking/CC-6/<ID>` | `feat(booking): CC-6 migrate config to application.yml (<ID>)` |
| 5 | `feat/calendar/CC-6/<ID>` | `feat(calendar): CC-6 migrate config to application.yml (<ID>)` |
| 6 | `feat/invoice/CC-6/<ID>` | `feat(invoice): CC-6 migrate config to application.yml (<ID>)` |
| 7 | `feat/user/MOD-0/<ID>` | `feat(user): MOD-0 add security/jwt/mongo/redis starters (<ID>)` |
| 8 | `feat/provider/MOD-0/<ID>` | `feat(provider): MOD-0 add security/jwt/mongo/redis/elasticsearch starters (<ID>)` |
| 9 | `feat/booking/MOD-0/<ID>` | `feat(booking): MOD-0 add security/jwt/mongo/redis/neo4j starters (<ID>)` |
| 10 | `feat/calendar/MOD-0/<ID>` | `feat(calendar): MOD-0 add security/jwt/mongo/redis/cassandra starters (<ID>)` |
| 11 | `feat/invoice/MOD-0/<ID>` | `feat(invoice): MOD-0 add security/jwt/mongo/redis starters (<ID>)` |
| 12 | `feat/cc/DP-5/<ID>` | `feat(cc): DP-5 introduce JwtConfigurationManager singleton and JwtService (<ID>)` |
| 13 | `feat/cc/DP-3/<ID>` | `feat(cc): DP-3 introduce AuthHandler chain and JwtAuthenticationFilter (<ID>)` |
| 14 | `feat/user/CC-1-security/<ID>` | `feat(user): CC-1 enforce JWT on all endpoints (DP-3) (<ID>)` |
| 15 | `feat/provider/CC-1-security/<ID>` | `feat(provider): CC-1 enforce JWT on all endpoints (DP-3) (<ID>)` |
| 16 | `feat/booking/CC-1-security/<ID>` | `feat(booking): CC-1 enforce JWT on all endpoints (DP-3) (<ID>)` |
| 17 | `feat/calendar/CC-1-security/<ID>` | `feat(calendar): CC-1 enforce JWT on all endpoints (DP-3) (<ID>)` |
| 18 | `feat/invoice/CC-1-security/<ID>` | `feat(invoice): CC-1 enforce JWT on all endpoints (DP-3) (<ID>)` |
| 19 | `feat/cc/DP-2/<ID>` | `feat(cc): DP-2 introduce Observer skeleton EntityObserver and MongoEventLogger (<ID>)` |
| 20 | `feat/cc/DP-6/<ID>` | `feat(cc): DP-6 introduce MongoEvent interface, EventFactory, EventType enum (<ID>)` |
| 21 | `feat/user/DP-2-mongoevent/<ID>` | `feat(user): DP-2 add AuthEvent document and repository (<ID>)` |
| 22 | `feat/provider/DP-2-mongoevent/<ID>` | `feat(provider): DP-2 add ProviderEvent document and repository (<ID>)` |
| 23 | `feat/booking/DP-2-mongoevent/<ID>` | `feat(booking): DP-2 add BookingEvent document and repository (<ID>)` |
| 24 | `feat/calendar/DP-2-mongoevent/<ID>` | `feat(calendar): DP-2 add CalendarEvent document and repository (<ID>)` |
| 25 | `feat/invoice/DP-2-mongoevent/<ID>` | `feat(invoice): DP-2 add PaymentAuditEvent with method/amount conditional rules (<ID>)` |
| 26 | `feat/provider/es-doc/<ID>` | `feat(provider): introduce ProviderSearchDocument and Elasticsearch repo (<ID>)` |
| 27 | `feat/booking/neo4j-entities/<ID>` | `feat(booking): introduce UserNode, ProviderNode, and BOOKED relationship (<ID>)` |
| 28 | `feat/calendar/cassandra-entity/<ID>` | `feat(calendar): introduce CalendarAvailabilityEvent Cassandra entity (<ID>)` |
| 29 | `feat/user/DP-7-adapters/<ID>` | `feat(user): DP-7 add MongoDocumentAdapter (<ID>)` |
| 30 | `feat/provider/DP-7-adapters/<ID>` | `feat(provider): DP-7 add MongoDocumentAdapter and ElasticsearchHitAdapter (<ID>)` |
| 31 | `feat/booking/DP-7-adapters/<ID>` | `feat(booking): DP-7 add MongoDocumentAdapter and Neo4jRecordAdapter (<ID>)` |
| 32 | `feat/calendar/DP-7-adapters/<ID>` | `feat(calendar): DP-7 add MongoDocumentAdapter and CassandraRowAdapter (<ID>)` |
| 33 | `feat/invoice/DP-7-adapters/<ID>` | `feat(invoice): DP-7 add MongoDocumentAdapter (<ID>)` |
| 34 | `feat/user/MOD-1/<ID>` | `feat(user): MOD-1 hash passwords with BCrypt and hide field in DTO (<ID>)` |
| 35 | `feat/user/MOD-2/<ID>` | `feat(user): MOD-2 seed ADMIN user and ignore role in register body (<ID>)` |
| 36 | `feat/provider/MOD-3/<ID>` | `feat(provider): MOD-3 add additive description key to serviceDetails (<ID>)` |
| 37 | `feat/invoice/MOD-4/<ID>` | `feat(invoice): MOD-4 add cancellationFee=0 to S5-F4 transactionDetails (<ID>)` |
| 38 | `feat/user/MOD-5-observer/<ID>` | `feat(user): MOD-5 wire Observer on M1 writes (DP-2) (<ID>)` |
| 39 | `feat/provider/MOD-5-observer/<ID>` | `feat(provider): MOD-5 wire Observer on M1 writes (DP-2) (<ID>)` |
| 40 | `feat/booking/MOD-5-observer/<ID>` | `feat(booking): MOD-5 wire Observer on M1 writes (DP-2) (<ID>)` |
| 41 | `feat/calendar/MOD-5-observer/<ID>` | `feat(calendar): MOD-5 wire Observer on M1 writes (DP-2) (<ID>)` |
| 42 | `feat/invoice/MOD-5-observer/<ID>` | `feat(invoice): MOD-5 wire Observer on M1 writes (DP-2) (<ID>)` |
| 43 | `feat/invoice/MOD-6/<ID>` | `feat(invoice): MOD-6 emit CREATED/COMPLETED/FAILED/REFUNDED audit events (DP-2) (<ID>)` |
| 44 | `feat/provider/MOD-7-autoindex/<ID>` | `feat(provider): MOD-7 auto-index providers to Elasticsearch on CRUD (DP-2) (<ID>)` |
| 45 | `feat/user/MOD-8-builder/<ID>` | `feat(user): MOD-8 retrofit Builder on S1 DTOs (DP-4) (<ID>)` |
| 46 | `feat/provider/MOD-8-builder/<ID>` | `feat(provider): MOD-8 retrofit Builder on S2 DTOs (DP-4) (<ID>)` |
| 47 | `feat/booking/MOD-8-builder/<ID>` | `feat(booking): MOD-8 retrofit Builder on S3 DTOs (DP-4) (<ID>)` |
| 48 | `feat/calendar/MOD-8-builder/<ID>` | `feat(calendar): MOD-8 retrofit Builder on S4 DTOs (DP-4) (<ID>)` |
| 49 | `feat/invoice/MOD-8-builder/<ID>` | `feat(invoice): MOD-8 retrofit Builder on S5 DTOs (DP-4) (<ID>)` |
| 50 | `feat/user/MOD-9-adapter/<ID>` | `feat(user): MOD-9 retrofit ObjectArrayDtoAdapter on S1-F3 (DP-7) (<ID>)` |
| 51 | `feat/provider/MOD-9-adapter/<ID>` | `feat(provider): MOD-9 retrofit Adapter on S2 Object[] mappings (DP-7) (<ID>)` |
| 52 | `feat/booking/MOD-9-adapter/<ID>` | `feat(booking): MOD-9 retrofit Adapter on S3 Object[] mappings (DP-7) (<ID>)` |
| 53 | `feat/calendar/MOD-9-adapter/<ID>` | `feat(calendar): MOD-9 retrofit Adapter on S4 Object[] mappings (DP-7) (<ID>)` |
| 54 | `feat/invoice/MOD-9-adapter/<ID>` | `feat(invoice): MOD-9 retrofit Adapter on S5 Object[] mappings (DP-7) (<ID>)` |
| 55 | `feat/user/CC-3-cache/<ID>` | `feat(user): CC-3 add Redis cache on M1 reads with wildcard invalidation (<ID>)` |
| 56 | `feat/provider/CC-3-cache/<ID>` | `feat(provider): CC-3 add Redis cache on M1 reads with wildcard invalidation (<ID>)` |
| 57 | `feat/booking/CC-3-cache/<ID>` | `feat(booking): CC-3 add Redis cache on M1 reads with wildcard invalidation (<ID>)` |
| 58 | `feat/calendar/CC-3-cache/<ID>` | `feat(calendar): CC-3 add Redis cache on M1 reads with wildcard invalidation (<ID>)` |
| 59 | `feat/invoice/CC-3-cache/<ID>` | `feat(invoice): CC-3 add Redis cache on M1 reads with wildcard invalidation (<ID>)` |
| 60 | `feat/cc/CC-2/<ID>` | `feat(cc): CC-2 role management endpoint with ROLE_CHANGED event (<ID>)` |

---

## Critical sequencing constraints (read before parallelizing)

| Wait until | Before starting |
|---|---|
| #1 (CC-5) merged | #2–#11 run |
| #1–#11 merged | #12 (DP-5) starts |
| #12 merged | #13 (DP-3) starts |
| #13 merged | #14–#18 (5 SecurityConfigs) |
| #14–#18 merged | #19 (DP-2) starts |
| #19 merged | #20 (DP-6) starts |
| #20 merged | #21–#25 (concrete event classes) |
| #26–#28 (NoSQL entities) and #29–#33 (adapters) | run after #25 |
| #34–#37 (M1 entity mods) | run after Phase 4 |
| Service's #38–#42 (Observer wiring) | merged before #43, #44 (audit + auto-index) |
| #45–#54 (Builder + Adapter retrofits) | run after #38–#44 |
| #55–#59 (caching) | run after #38–#44 |
| #60 (CC-2) | runs after caching merged |
| All 60 merged | Phase 11 gate |
| Phase 11 PASSED | The 15 M2 features (out of scope here) |

---

## Gotchas digest (one-liners every agent should re-read before each phase)

1. **Always pin `<ID>` to the validated `team.json` row at session start.** Never invent.
2. **Never squash-merge.** Auto-grader extracts the branch name from the merge commit message — squash deletes it.
3. **Never delete a feature branch after merge.** Auto-grader scans for branch names.
4. **Add the `DP-<n>` token to every commit message that touches a design pattern**, even when the branch isn't `feat/cc/DP-<n>/...`.
5. **Verbatim copy of shared classes is the team's chosen strategy.** Treat the canonical file as the source of truth; mirror it byte-for-byte (modulo package declaration) into the other 4 services. Add a `chore` commit with subject `chore(<service>): verbatim copy of <X> from <canonical-service>` so review can spot drift.
6. **The JWT secret must be the same Base64 string in all 5 application.yml files.** Coordinate the value before any agent edits a yml.
7. **MongoEvent action values are UPPER_SNAKE_CASE.** Case mismatch makes S5-F11 silently drop events.
8. **PaymentAuditEvent `method` and `amount` are required for payment-shaped actions.** Null only on `ANALYTICS_VIEWED`.
9. **`@EventListener` must NOT write to MongoDB.** Static-scan failure on any such method.
10. **`new XxxEvent(...)` is forbidden in services.** Always go through `EventFactory`.
11. **JPA listener (`@PostPersist` etc.) cannot @Autowired Spring beans.** Prefer service-level hooks.
12. **`disableCachingNullValues()` is required.** Caching nulls poisons future reads.
13. **Spring Cache + Redis needs `spring.cache.type: redis`** explicitly — without it you fall back to in-memory.
14. **Wildcard-delete via SCAN, not KEYS.** KEYS blocks Redis and times out the test.
15. **Observer-driven cache invalidation must filter out `ANALYTICS_VIEWED` and `DASHBOARD_VIEWED`.** Otherwise dashboards self-defeat their cache.
16. **Re-running M1 manual tests under JWT** is the regression checkpoint — feature work cannot start until they pass.
17. **The grader runs reflection tests.** Method names, return types, modifiers, annotation absence all checked. Match signatures exactly to the spec.
18. **Strategy is M2-only (S5-F12).** Grep-test fails the project if any M1 service uses a `Strategy` class.
19. **`role` claim in JWT is the literal enum name (`CLIENT`, `ADMIN`).** Spring Security's `hasRole("ADMIN")` matches authority `ROLE_ADMIN` — convert with `"ROLE_" + claim`.
20. **Token-staleness after CC-2 is an accepted limitation.** Do not invent a revocation list — it is not required and may diverge from the spec.

---

## Verification methodology — the test/edit/verify loop

Every phase ends with this loop. The implementing agent **does not declare the phase done** until all of the following are green:

1. Run the MS2-spec scenarios listed in this plan for the phase.
2. Run the additional integration tests listed in this plan for the phase.
3. Run the cross-phase regression tests (M1 manual tests + soft-dep tests + pattern reflection sweep).
4. Run the team's existing `mvn test` suite — no regressions.
5. If anything fails:
   - **Read the failure carefully.** Do not assume the cause.
   - **Re-read the relevant spec section.** §X.Y references in this plan cite exactly where the rule is defined.
   - **Edit the minimum scope that fixes the failure.** No drive-by refactors.
   - **Re-run the entire failing block.** Loop.
6. When green: open the PR, request a teammate review, merge as a regular merge commit.
7. Update the team checklist (one row per branch in this document's table) and unblock dependent agents.

The agent should treat this loop as the definition of "done" for any phase. A phase is not complete because the code compiles or because one happy-path curl returned 200 — it is complete when every test scenario in this plan and every additional integration test passes.

---

## Files this plan references (full paths)

For agents whose context window doesn't include a tree, here are the exact file paths to be created or modified, grouped by phase:

```
# Phase 2A
docker-compose.yaml

# Phase 2B (×5)
{user,provider,booking,calendar,invoice}-service/src/main/resources/application.yml
{user,provider,booking,calendar,invoice}-service/src/main/resources/application.properties     # delete

# Phase 2C
pom.xml
{user,provider,booking,calendar,invoice}-service/pom.xml

# Phase 3 (canonical in user-service, then verbatim copies)
{service}-service/src/main/java/com/team28/booking/{service}/auth/JwtConfigurationManager.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/JwtService.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/JwtAuthenticationFilter.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/handlers/AuthContext.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/handlers/AuthHandler.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/handlers/TokenExtractionHandler.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/handlers/SignatureValidationHandler.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/handlers/UserLoaderHandler.java
{service}-service/src/main/java/com/team28/booking/{service}/auth/handlers/RoleAuthorizationHandler.java
{service}-service/src/main/java/com/team28/booking/{service}/config/SecurityConfig.java

# Phase 4A (canonical in user-service, verbatim copies)
{service}-service/src/main/java/com/team28/booking/{service}/observer/EntityObserver.java
{service}-service/src/main/java/com/team28/booking/{service}/observer/Observable.java
{service}-service/src/main/java/com/team28/booking/{service}/observer/MongoEventLogger.java

# Phase 4B (canonical in invoice-service, verbatim copies)
{service}-service/src/main/java/com/team28/booking/{service}/factory/MongoEvent.java
{service}-service/src/main/java/com/team28/booking/{service}/factory/EventType.java
{service}-service/src/main/java/com/team28/booking/{service}/factory/EventFactory.java

# Phase 4C — concrete event class per service
user-service/src/main/java/com/team28/booking/user/mongo/AuthEvent.java
user-service/src/main/java/com/team28/booking/user/mongo/AuthEventRepository.java
provider-service/src/main/java/com/team28/booking/provider/mongo/ProviderEvent.java
provider-service/src/main/java/com/team28/booking/provider/mongo/ProviderEventRepository.java
booking-service/src/main/java/com/team28/booking/booking/mongo/BookingEvent.java
booking-service/src/main/java/com/team28/booking/booking/mongo/BookingEventRepository.java
calendar-service/src/main/java/com/team28/booking/calendar/mongo/CalendarEvent.java
calendar-service/src/main/java/com/team28/booking/calendar/mongo/CalendarEventRepository.java
invoice-service/src/main/java/com/team28/booking/invoice/mongo/PaymentAuditEvent.java
invoice-service/src/main/java/com/team28/booking/invoice/mongo/PaymentAuditEventRepository.java

# Phase 4D NoSQL entities
provider-service/src/main/java/com/team28/booking/provider/search/ProviderSearchDocument.java
provider-service/src/main/java/com/team28/booking/provider/search/ProviderSearchRepository.java
booking-service/src/main/java/com/team28/booking/booking/neo4j/UserNode.java
booking-service/src/main/java/com/team28/booking/booking/neo4j/ProviderNode.java
booking-service/src/main/java/com/team28/booking/booking/neo4j/BookedRelationship.java
booking-service/src/main/java/com/team28/booking/booking/neo4j/UserNodeRepository.java
booking-service/src/main/java/com/team28/booking/booking/neo4j/ProviderNodeRepository.java
calendar-service/src/main/java/com/team28/booking/calendar/cassandra/CalendarAvailabilityEvent.java
calendar-service/src/main/java/com/team28/booking/calendar/cassandra/CalendarAvailabilityEventRepository.java

# Phase 4E — adapters per service
{service}-service/src/main/java/com/team28/booking/{service}/adapter/MongoDocumentAdapter.java
provider-service/src/main/java/com/team28/booking/provider/adapter/ElasticsearchHitAdapter.java
booking-service/src/main/java/com/team28/booking/booking/adapter/Neo4jRecordAdapter.java
calendar-service/src/main/java/com/team28/booking/calendar/adapter/CassandraRowAdapter.java

# Phase 5
user-service/src/main/java/com/team28/booking/user/service/UserService.java       # modify
user-service/src/main/java/com/team28/booking/user/dto/UserDTO.java               # modify (omit password)
user-service/src/main/java/com/team28/booking/user/seed/Seed.java                 # modify (BCrypt + ADMIN row)

# Phase 6
provider-service/src/main/java/com/team28/booking/provider/service/ProviderService.java   # modify (description)
invoice-service/src/main/java/com/team28/booking/invoice/service/InvoiceService.java      # modify (cancellationFee=0)

# Phase 7
{service}-service/src/main/java/com/team28/booking/{service}/service/*.java       # modify — Observer wiring
invoice-service/src/main/java/com/team28/booking/invoice/controller/InvoiceController.java  # ?simulateFailure
provider-service/src/main/java/com/team28/booking/provider/service/IndexingService.java     # new — auto-index

# Phase 8
{service}-service/src/main/java/com/team28/booking/{service}/dto/*.java           # modify — Builder retrofit
{service}-service/src/main/java/com/team28/booking/{service}/adapter/ObjectArrayDtoAdapter.java   # new where applicable

# Phase 9
{service}-service/src/main/java/com/team28/booking/{service}/config/CacheConfig.java        # new
{service}-service/src/main/java/com/team28/booking/{service}/cache/CacheInvalidator.java    # new
{service}-service/src/main/java/com/team28/booking/{service}/controller/*.java              # modify — @Cacheable
{service}-service/src/main/java/com/team28/booking/{service}/service/*.java                 # modify — invalidator calls

# Phase 10
user-service/src/main/java/com/team28/booking/user/controller/UserController.java          # add updateRole
user-service/src/main/java/com/team28/booking/user/service/UserService.java                # add changeRole
user-service/src/main/java/com/team28/booking/user/dto/UpdateRoleRequest.java              # new
```

---

## When Phase 11 passes

Post in the team channel: **"Phase 11 GATE PASSED on YYYY-MM-DD. The 15 M2 features are unblocked. Per-feature branch list per the cross-cutting plan's per-team table."** Each member then opens their `feat/<service>/S<n>-F<m>/<ID>` branch and ships their feature.
