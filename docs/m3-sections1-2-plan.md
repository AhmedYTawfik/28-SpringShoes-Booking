# M3 Implementation Plan — Sections 1 & 2 + Pre-Development

> **Scope:** Day-0 Initialization · Section 1 (Database Isolation) · Section 2 (Inter-Service Communication Setup)  
> **Team:** 28-SpringShoes · Package: `com.team28.booking` · 14 members · 2 on calendar-service

---

## Table of Contents

1. [Overview & Dependencies](#1-overview--dependencies)
2. [Phase 0 — Day-0 Kickoff (Pre-Development)](#2-phase-0--day-0-kickoff)
3. [Phase 1 — Section 1: Database Isolation](#3-phase-1--section-1-database-isolation)
4. [Phase 2 — Section 2: Inter-Service Communication](#4-phase-2--section-2-inter-service-communication)
5. [Concerns & Options](#5-concerns--options)
6. [Gotchas & Pitfalls](#6-gotchas--pitfalls)
7. [Test Scenarios](#7-test-scenarios)

---

## 1. Overview & Dependencies

### What These Sections Deliver

| Section | Core Deliverable | Touches |
|---------|-----------------|---------|
| **Pre-Dev (Day 0)** | `contracts/` module with all Feign interfaces, DTOs, event records | Root `pom.xml`, new module |
| **Section 1** | Each service gets its own PostgreSQL; cross-service FKs → plain `Long` | 5× `application.yml`, entity classes, Booking enum |
| **Section 2** | OpenFeign + RabbitMQ wiring across all services | `pom.xml` deps, `@EnableFeignClients`, Feign configs, RabbitMQ configs, event topology |

### Prerequisites

- M2 codebase fully merged to `main` and passing
- All 5 services compile and run against current shared DB
- Docker Compose available for local multi-DB testing

---

## 2. Phase 0 — Day-0 Kickoff

> **CRITICAL:** This MUST be completed before any feature slices begin. It unblocks all 15 parallel slices.

### 2.1 Git Workflow

```
Branch:  feat/M3/contracts/day0-kickoff/<studentID>
Commits:
  1. feat(contracts): create contracts module with pom.xml (<studentID>)
  2. feat(contracts): add all Feign client interfaces (<studentID>)
  3. feat(contracts): add all shared DTOs (<studentID>)
  4. feat(contracts): add all event payload records (<studentID>)
  5. feat(root): add contracts + api-gateway modules to parent pom.xml (<studentID>)
Merge: PR → main (team lead reviews, fast-track merge)
```

### 2.2 Create `contracts/` Maven Module

**File:** `contracts/pom.xml`

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.team28.booking</groupId>
        <artifactId>booking</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <artifactId>contracts</artifactId>
    <packaging>jar</packaging>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

> **WARNING:** The `contracts` module needs the Spring Cloud BOM in scope. Either add `dependencyManagement` here or ensure the parent POM provides it. Without the BOM, `spring-cloud-starter-openfeign` won't resolve a version.

### 2.3 Update Root `pom.xml`

Add `contracts` as the **first** module (Maven reactor builds it before services):

```xml
<modules>
    <module>contracts</module>
    <module>user-service</module>
    <module>provider-service</module>
    <module>booking-service</module>
    <module>calendar-service</module>
    <module>invoice-service</module>
    <module>api-gateway</module>
</modules>
```

Also add Spring Cloud BOM to `<dependencyManagement>`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2.4 Feign Client Interfaces (in `contracts/`)

Package: `com.team28.booking.contracts.feign`

| Interface | Methods | Used By |
|-----------|---------|---------|
| `UserServiceClient` | `getUser(Long id)` | S2, S3, S5 |
| `ProviderServiceClient` | `getProvider(Long id)`, `getProviderAvailability(Long id)` | S1, S3, S4 |
| `BookingServiceClient` | `getUserBookingSummary`, `getActiveBookingCount`, `getCompletedBookingCount`, `getProviderBookingSummary`, `getProviderActiveCount`, `getProviderCompletedCount`, `getBooking` | S1, S2, S4, S5 |
| `CalendarServiceClient` | `getSlotForBooking(Long providerId, String date, String startTime)`, `getProviderUtilization` | S2, S3 |
| `InvoiceServiceClient` | `getUserInvoiceTotal`, `getInvoiceAmountsByBookings` | S1, S3 |

### 2.5 Shared DTOs (in `contracts/`)

Package: `com.team28.booking.contracts.dto`

Files: `UserDTO`, `ProviderDTO`, `ProviderAvailabilityDTO`, `BookingDTO`, `BookingSummaryDTO`, `ProviderBookingSummaryDTO`, `TimeSlotDTO`, `ProviderUtilizationDTO`, `InvoiceAmountDTO`, `InvoiceAmountsRequest`

### 2.6 Event Payload Records (in `contracts/`)

Package: `com.team28.booking.contracts.events`

14 record classes — all listed in §2.8 of the milestone spec. Each is a plain Java `record`:

```java
public record BookingPlacedEvent(Long bookingId, Long userId, Long providerId) {}
public record BookingCompletedEvent(Long bookingId, Long userId, Long providerId, Double totalPrice) {}
// ... (12 more — see §2.8 of spec)
```

### 2.7 Each Service Depends on `contracts`

Add to each service's `pom.xml`:

```xml
<dependency>
    <groupId>com.team28.booking</groupId>
    <artifactId>contracts</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

> **CAUTION:** `api-gateway` does NOT depend on `contracts`. It only forwards HTTP.

### 2.8 Stub YAML Files

Create placeholder files that each slice will fill in later:
- `api-gateway/src/main/resources/application.yml` — route stubs
- Prometheus scrape config placeholder
- Grafana dashboards ConfigMap placeholder

---

## 3. Phase 1 — Section 1: Database Isolation

### 3.1 Git Workflow

Each service's DB isolation is part of its `S<N>-READ-DB` slice:

```
Branch:  feat/M3/<service>/S<N>-READ-DB/<studentID>
Example: feat/M3/calendar/S4-READ-DB/55-8947

Commits:
  1. feat(calendar): isolate datasource to calendar-postgres/bookingdb-calendar (<studentID>)
  2. feat(calendar): verify cross-service columns are plain Long (<studentID>)
```

### 3.2 Per-Service Datasource Changes

| Service | Old URL | New URL | New Host |
|---------|---------|---------|----------|
| user-service | `postgres:5432/bookingdb` | `user-postgres:5432/bookingdb-users` | `user-postgres` |
| provider-service | `postgres:5432/bookingdb` | `provider-postgres:5432/bookingdb-providers` | `provider-postgres` |
| booking-service | `postgres:5432/bookingdb` | `booking-postgres:5432/bookingdb-bookings` | `booking-postgres` |
| calendar-service | `postgres:5432/bookingdb` | `calendar-postgres:5432/bookingdb-calendar` | `calendar-postgres` |
| invoice-service | `postgres:5432/bookingdb` | `invoice-postgres:5432/bookingdb-invoices` | `invoice-postgres` |

**calendar-service `application.yml` change:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:calendar-postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:bookingdb-calendar}
```

### 3.3 Cross-Service FK Verification

Per the spec, M1 already stores `userId`, `providerId`, `bookingId` as plain `Long`. **Verify** — do NOT assume. Check each entity:

- `TimeSlot.providerId` — already `private Long providerId` ✅ (confirmed from source)
- `Booking.userId`, `Booking.providerId` — must verify
- `Invoice.bookingId`, `Invoice.userId` — must verify

> **TIP:** Run `grep -rn "@ManyToOne\|@JoinColumn" */src/main/java/` across all services to catch any remaining JPA relationships that cross service boundaries.

### 3.4 New Booking Status Enum Values

Add to `Booking` entity's status enum (in **booking-service**):

| New Status | Purpose |
|-----------|---------|
| `COMPLETING` | Set before publishing `booking.completed` |
| `PAYMENT_PENDING` | Set on `payment.initiated` consumption |
| `PAID` | Set on `payment.completed` consumption |
| `PAYMENT_FAILED` | Set on `payment.failed` consumption |
| `REFUNDED` | Set on `payment.refunded` consumption |

Existing values (`REQUESTED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) remain unchanged.

### 3.5 Docker Compose Update

Update `docker-compose.yaml` to run **5 separate PostgreSQL containers**:

```yaml
user-postgres:
  image: postgres:17
  environment:
    POSTGRES_DB: bookingdb-users
    POSTGRES_USER: user
    POSTGRES_PASSWORD: password
  ports: ["5433:5432"]

provider-postgres:
  image: postgres:17
  # ... port 5434

booking-postgres:
  image: postgres:17
  # ... port 5435

calendar-postgres:
  image: postgres:17
  environment:
    POSTGRES_DB: bookingdb-calendar
    POSTGRES_USER: user
    POSTGRES_PASSWORD: password
  ports: ["5436:5432"]

invoice-postgres:
  image: postgres:17
  # ... port 5437
```

> **WARNING:** **PostgreSQL 17 only** — NOT 18. PG18 breaks Hibernate native query implicit cast operator resolution (§16 Critical Rule 5).

---

## 4. Phase 2 — Section 2: Inter-Service Communication

### 4.1 Git Workflow

Feign setup is part of each service's `S<N>-READ-DB` slice. RabbitMQ setup is part of `S<N>-EVENTS` slice.

```
# Feign (within READ-DB branch)
Commits:
  feat(<service>): add OpenFeign dependency and @EnableFeignClients (<studentID>)
  feat(<service>): add FeignCorrelationConfig for X-Correlation-ID (<studentID>)
  feat(<service>): configure feign service URLs in application.yml (<studentID>)

# RabbitMQ (within EVENTS branch)
Branch: feat/M3/<service>/S<N>-EVENTS/<studentID>
Commits:
  feat(<service>): add spring-boot-starter-amqp dependency (<studentID>)
  feat(<service>): add RabbitMQ connection config to application.yml (<studentID>)
  feat(<service>): create <Service>EventConfig with exchange + queues + DLQ (<studentID>)
  feat(<service>): implement event publishers (<studentID>)
  feat(<service>): implement event consumers with idempotency guards (<studentID>)
```

### 4.2 OpenFeign Setup (Per Service)

#### Step 1: Add Dependencies to `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

Plus Spring Cloud BOM in `<dependencyManagement>` (if not inherited from parent).

#### Step 2: Enable on Application Class

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class CalendarServiceApplication { }
```

> **IMPORTANT:** The `basePackages` must point to `com.team28.booking.contracts.feign` since the Feign interfaces live in the `contracts` module, not the service's own package. Without this, Spring won't scan them.

#### Step 3: Add Feign URLs to `application.yml`

```yaml
feign:
  provider-service:
    url: http://${FEIGN_PROVIDER_SERVICE_URL:provider-service:8080}
```

#### Step 4: Correlation ID Interceptor

```java
@Configuration
public class FeignCorrelationConfig {
    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }
}
```

#### Step 5: Error Handling Pattern

**Every** Feign call must be wrapped:

```java
try {
    ProviderDTO provider = providerServiceClient.getProvider(providerId);
    // use provider
} catch (FeignException.NotFound e) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
} catch (FeignException e) {
    log.warn("provider-service unavailable: {}", e.getMessage());
    throw new ServiceUnavailableException("Provider service temporarily unavailable");
}
```

### 4.3 RabbitMQ Setup (Per Service)

#### Step 1: Add Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

#### Step 2: Connection Config in `application.yml`

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
```

#### Step 3: Topology Declaration (Example — Calendar Service)

```java
@Configuration
public class CalendarEventConfig {
    // PRODUCER: exchange this service publishes to
    @Bean
    public TopicExchange calendarEventsExchange() {
        return new TopicExchange("calendar.events");
    }

    // CONSUMER: reference to the exchange we consume from
    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    // Main queue
    @Bean
    public Queue calendarBookingSagaQueue() {
        return QueueBuilder.durable("calendar.booking.saga-listener")
            .withArgument("x-dead-letter-exchange", "calendar.dlx")
            .withArgument("x-dead-letter-routing-key", "calendar.booking.saga-listener.dlq")
            .build();
    }

    // Dead-letter exchange + queue
    @Bean
    public TopicExchange calendarDlx() {
        return new TopicExchange("calendar.dlx");
    }

    @Bean
    public Queue calendarBookingSagaDlq() {
        return QueueBuilder.durable("calendar.booking.saga-listener.dlq").build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(calendarBookingSagaDlq())
            .to(calendarDlx()).with("calendar.booking.saga-listener.dlq");
    }

    // Bindings: routing keys → consumer queue
    @Bean
    public Binding bookingPlacedBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue())
            .to(bookingEventsExchange()).with("booking.placed");
    }

    @Bean
    public Binding bookingCompletedBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue())
            .to(bookingEventsExchange()).with("booking.completed");
    }

    @Bean
    public Binding bookingCancelledBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue())
            .to(bookingEventsExchange()).with("booking.cancelled");
    }
}
```

> **CAUTION:** Producer declares only the exchange. Consumer declares queue + DLQ + bindings. Don't declare queues in the producer service.

### 4.4 Event Map Summary

| Exchange | Publisher | Routing Keys | Consumers |
|----------|-----------|-------------|-----------|
| `user.events` | user-service | `user.registered`, `user.deactivated` | *(observability only)* |
| `provider.events` | provider-service | `provider.status-changed`, `provider.rated`, `provider.certification.verified` | *(observability only)* |
| `booking.events` | booking-service | `booking.placed`, `booking.completed`, `booking.cancelled` | S1, S2, S4, S5 |
| `calendar.events` | calendar-service | `slot.reserved`, `slot.released` | *(observability only, optional)* |
| `payment.events` | invoice-service | `payment.initiated/completed/failed/refunded` | S3 (booking-service) |

### 4.5 JSON MessageConverter (Required)

```java
@Bean
public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

Without this, RabbitMQ defaults to Java serialization and `record` classes will fail deserialization.

### 4.6 Docker Compose — Add RabbitMQ

```yaml
rabbitmq:
  image: rabbitmq:3-management
  ports:
    - "5672:5672"
    - "15672:15672"
  environment:
    RABBITMQ_DEFAULT_USER: guest
    RABBITMQ_DEFAULT_PASS: guest
```

---

## 5. Concerns & Options

### 5.1 Contracts Module — Version Alignment

| Concern | Impact | Recommendation |
|---------|--------|----------------|
| Parent POM version mismatch | If anyone changes root version, contracts breaks | Pin version explicitly in `contracts/pom.xml` |
| Spring Cloud BOM location | Could live in root POM or contracts POM | **Put in root POM** — all services need it for Feign |

### 5.2 Data Migration Strategy

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| **A: Fresh DBs (ddl-auto=update)** | Simple, no migration scripts | Lose existing test data | ✅ **Use this** — grader seeds its own data |
| **B: pg_dump per table set** | Preserve data | Complex, error-prone | ❌ |
| **C: Fresh DBs + seed scripts** | Clean + reproducible | Extra work | Only if needed |

### 5.3 Feign URL Resolution

| Environment | Strategy |
|-------------|----------|
| Docker Compose | Service names in compose network (`http://provider-service:8080`) |
| Kubernetes | ClusterIP service DNS (`http://provider-service:8080`) |
| Local dev (no Docker) | `http://localhost:<port>` via env var override |

Use env vars with Docker-friendly defaults: `${FEIGN_PROVIDER_SERVICE_URL:http://provider-service:8080}`

### 5.4 Jackson for Event Records

Java `record` classes work with Jackson ≥ 2.12. Spring Boot 4.x bundles 2.17+, so this is safe. But `Jackson2JsonMessageConverter` bean is **mandatory** — see §4.5.

---

## 6. Gotchas & Pitfalls

### 6.1 Database

1. **PG17, not PG18** — Hibernate native queries break on PG18 implicit casts
2. **`ddl-auto: update`** — creates tables in new DB but doesn't migrate data
3. **Different hostnames, not just different DB names** — each service uses a different PostgreSQL *instance*
4. **NoSQL databases remain shared** — MongoDB, Redis, ES, Neo4j, Cassandra stay as single instances

### 6.2 Feign

5. **`basePackages` on `@EnableFeignClients`** — must point to `com.team28.booking.contracts.feign`, not the service's own package
6. **Always wrap Feign calls in try-catch** — a 404 from downstream must not become a 500 in the caller
7. **No circular Feign calls** — verify your endpoint graph
8. **DTO field name mismatch** — Jackson silently returns nulls for mismatched `@JsonProperty`

### 6.3 RabbitMQ

9. **`acknowledge-mode: auto` + `default-requeue-rejected: false`** — mandatory combo; without `false`, failed messages loop forever
10. **Every queue needs a DLQ** — use `x-dead-letter-exchange` + `x-dead-letter-routing-key` arguments
11. **`Jackson2JsonMessageConverter` bean** — without it, `record` classes can't be deserialized
12. **Idempotent consumers** — use `UPDATE ... WHERE status IN (<allowed>)` guards; never read-then-write in separate statements
13. **Exchange type is `TopicExchange`** — not Direct, not Fanout

### 6.4 Contracts Module

14. **No Spring Boot auto-configuration** — it's a plain JAR, no `@SpringBootApplication`, no `application.yml`
15. **No business logic** — only interfaces, DTOs, and event records
16. **Version must match parent** — root POM is `1.0-SNAPSHOT`, contracts must be `1.0-SNAPSHOT`

---

## 7. Test Scenarios

### 7.1 Database Isolation Tests

| # | Test | Steps | Expected |
|---|------|-------|----------|
| T1.1 | Service starts against isolated DB | Start calendar-service pointing to `calendar-postgres:5432/bookingdb-calendar` | Service boots, Hibernate creates `time_slots` table |
| T1.2 | No cross-DB JDBC | Run `SELECT * FROM providers` from calendar-service | SQL exception (table doesn't exist in calendar-postgres) |
| T1.3 | Cross-service columns are plain Long | Inspect `TimeSlot.providerId` | `private Long providerId` — no `@ManyToOne` |
| T1.4 | Booking enum has saga statuses | Check Booking entity | All 5 new values compile |
| T1.5 | All 5 services start simultaneously | `docker compose up` with 5 PG containers | All services healthy |

### 7.2 OpenFeign Tests

| # | Test | Steps | Expected |
|---|------|-------|----------|
| T2.1 | Feign call succeeds | calendar→provider `getProvider(1)` | Returns `ProviderDTO` correctly |
| T2.2 | Feign 404 handling | Call with non-existent ID | `FeignException.NotFound` caught → 404 |
| T2.3 | Feign service down | Stop provider-service, make call | `FeignException` caught → 503 or fallback |
| T2.4 | Correlation ID propagated | Set header, make Feign call | Downstream receives `X-Correlation-ID` |
| T2.5 | Contracts compiles independently | `mvn clean install -pl contracts` | JAR produced |
| T2.6 | Service compiles with contracts | `mvn clean compile -pl calendar-service` | Compiles, Feign resolvable |
| T2.7 | Query params forwarded | `getProviderUtilization(1, "2026-03-01", "2026-03-31")` | Params in URL |

### 7.3 RabbitMQ Tests

| # | Test | Steps | Expected |
|---|------|-------|----------|
| T3.1 | Exchange created | Start service, check management UI | `calendar.events` exists |
| T3.2 | Queue + DLQ created | Start consumer service | Queue with DLQ arguments exists |
| T3.3 | Publish event | Publish to `booking.events` | Message arrives in bound queues |
| T3.4 | Consumer processes event | Publish `booking.placed` | Matching slot → `available=false` |
| T3.5 | Failed → DLQ | Publish malformed event | After 3 retries, message in DLQ |
| T3.6 | Idempotent consumption | Publish same event twice | Second delivery is no-op |
| T3.7 | JSON serialization | Publish `BookingPlacedEvent` record | Consumer deserializes correctly |

### 7.4 Integration Tests

| # | Test | Steps | Expected |
|---|------|-------|----------|
| T4.1 | Full compose startup | 5 PG + RabbitMQ + 5 services | All healthy, queues auto-created |
| T4.2 | Feign + isolated DB E2E | Create provider, query via Feign | Data returned without direct DB access |
| T4.3 | Event E2E | Trigger booking.placed → verify consumer | Slot booked in calendar-postgres |
| T4.4 | No shared DB connections | Check connection pools | Each service → only its own PG |
