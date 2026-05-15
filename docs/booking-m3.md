# Milestone 3 — Booking & Scheduling Platform

## True Microservices: Service Isolation, Inter-Service Communication & Kubernetes

> **Weight:** 40% of final grade  
> **Theme:** Booking & Scheduling
>
> **Deadline:** Saturday 17/05/2026 at 11:59 PM

---

## Services in This Theme

| Service          | Module name        | Internal port | Database (M3)         |
| ---------------- | ------------------ | ------------- | --------------------- |
| User Service     | `user-service`     | 8080          | `bookingdb-users`     |
| Provider Service | `provider-service` | 8080          | `bookingdb-providers` |
| Booking Service  | `booking-service`  | 8080          | `bookingdb-bookings`  |
| Calendar Service | `calendar-service` | 8080          | `bookingdb-calendar`  |
| Invoice Service  | `invoice-service`  | 8080          | `bookingdb-invoices`  |
| API Gateway      | `api-gateway`      | 8080          | —                     |

---

## What M3 Adds to Your Codebase

M1 built 5 services sharing one PostgreSQL database.  
M2 added 6 databases (polyglot persistence), authentication, caching, and design patterns — still one PostgreSQL, still cross-service SQL JOINs inside that PostgreSQL.  
M3 finishes the transformation:

- **Database isolation** — each service gets its own PostgreSQL instance. No service can open a JDBC connection to another service's database.
- **OpenFeign** — synchronous HTTP calls replace cross-service SQL JOINs for read dependencies.
- **RabbitMQ** — asynchronous events replace cross-service write side-effects.
- **Spring Cloud Gateway** — a 6th Maven module acts as the single entry point. JWT validation moves here.
- **Kubernetes** — all services and databases deploy to a local MiniKube cluster.

### What Does NOT Change

- All 45 M1 features — except the cross-service SQL inside 18 of them (see sections below)
- All 7 M2 design patterns
- All 6 M2 databases (PostgreSQL + MongoDB + Redis + Elasticsearch + Neo4j + Cassandra)
- JWT authentication (shared secret, stays the same)
- Redis caching (all cached endpoints remain cached)
- MongoDB event logging (Observer pattern stays in place)

### New Booking Status Values

M3 adds saga-related statuses to the `Booking` entity's status enum:

| New status        | When it is set                                                       |
| ----------------- | -------------------------------------------------------------------- |
| `COMPLETING`      | S3 sets this immediately before publishing `booking.completed`       |
| `PAYMENT_PENDING` | S3 sets this when `payment.initiated` event is consumed              |
| `PAID`            | S3 sets this when `payment.completed` event is consumed              |
| `PAYMENT_FAILED`  | S3 sets this when `payment.failed` event is consumed                 |
| `REFUNDED`        | S3 sets this when `payment.refunded` event is consumed               |

The existing `REQUESTED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED` values from M1/M2 remain unchanged. M1's `COMPLETED` keeps its terminal "appointment fully done in M1 flow" semantic; the saga in M3 introduces `COMPLETING` so the saga state machine never overwrites M1 semantics.

---

## Section 1 — Database Isolation

### 1.1 What Changes

Every service previously connected to a single shared database:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/bookingdb
```

In M3, each service connects to its own database on its own PostgreSQL instance:

```yaml
# user-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://user-postgres:5432/bookingdb-users

# provider-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://provider-postgres:5432/bookingdb-providers

# booking-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://booking-postgres:5432/bookingdb-bookings

# calendar-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://calendar-postgres:5432/bookingdb-calendar

# invoice-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://invoice-postgres:5432/bookingdb-invoices
```

### 1.2 Cross-Service FK Columns Become Plain Longs

Every `@ManyToOne` or `@JoinColumn` that pointed to another service's entity becomes a plain `Long` field. The column still exists in the database, but there is no JPA foreign-key relationship across databases. M1 already stored `userId`, `providerId`, and `bookingId` as plain `Long` columns (per §7 of the M1 spec) — there is nothing to convert there. The intra-service relationships remain JPA-managed.

| Table                     | Column         | Before (M1/M2)                | After (M3)                 |
| ------------------------- | -------------- | ----------------------------- | -------------------------- |
| `bookings`                | `user_id`      | `Long` (already plain)        | `private Long userId;`     |
| `bookings`                | `provider_id`  | `Long` (already plain)        | `private Long providerId;` |
| `time_slots`              | `provider_id`  | `Long` (already plain)        | `private Long providerId;` |
| `invoices`                | `booking_id`   | `Long` (already plain)        | `private Long bookingId;`  |
| `invoices`                | `user_id`      | `Long` (already plain)        | `private Long userId;`     |
| `provider_certifications` | `provider_id`  | `@ManyToOne Provider provider`| unchanged (same service)   |
| `saved_addresses`         | `user_id`      | `@ManyToOne User user`        | unchanged (same service)   |
| `booking_services`        | `booking_id`   | `@ManyToOne Booking booking`  | unchanged (same service)   |

The intra-service `@ManyToOne` relationships (`SavedAddress→User`, `ProviderCertification→Provider`, `BookingService→Booking`, `InvoiceDiscount→Invoice`, `InvoiceDiscount→Discount`) all stay as JPA-managed because both sides live in the same service's database.

### 1.3 NoSQL Databases — Shared Instance, Separate Ownership

MongoDB, Redis, Elasticsearch, Neo4j, and Cassandra remain as **single shared instances** (one StatefulSet each in Kubernetes). Each service already owns its own collections/indexes/keyspace and never reads another service's data — the logical isolation from M2 is sufficient. Running 5 MongoDB + 5 Redis + 5 Elasticsearch + 5 Neo4j + 5 Cassandra StatefulSets would make MiniKube unrunnable.

**The M3 rule:** No service connects to another service's PostgreSQL. Each service continues to own its MongoDB collections (`auth_events`, `provider_events`, `booking_events`, `calendar_events`, `payment_audit_trail`), Redis key prefix (`user-service::*`, `provider-service::*`, etc.), Elasticsearch index (`providers` — provider-service), Neo4j label set (`UserNode`, `ProviderNode`, `BOOKED` — booking-service), and Cassandra keyspace (`calendar_availability_events` — calendar-service).

### 1.4 Deliverables for DB Isolation

- [ ] `user-service/application.yml` — datasource URL points to `user-postgres:5432/bookingdb-users`
- [ ] `provider-service/application.yml` — datasource URL points to `provider-postgres:5432/bookingdb-providers`
- [ ] `booking-service/application.yml` — datasource URL points to `booking-postgres:5432/bookingdb-bookings`
- [ ] `calendar-service/application.yml` — datasource URL points to `calendar-postgres:5432/bookingdb-calendar`
- [ ] `invoice-service/application.yml` — datasource URL points to `invoice-postgres:5432/bookingdb-invoices`
- [ ] All cross-service `@ManyToOne` fields replaced with `Long` in the relevant entities (verified — M1 already plain `Long`)
- [ ] New saga statuses added to `Booking` status enum (COMPLETING, PAYMENT_PENDING, PAID, PAYMENT_FAILED, REFUNDED)

---

## Section 2 — Inter-Service Communication Setup

### 2.1 OpenFeign Dependency

Add to every service that makes Feign calls:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

Add Spring Cloud BOM to `dependencyManagement`:

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

Enable on `@SpringBootApplication`:

```java
@SpringBootApplication
@EnableFeignClients
public class UserServiceApplication { }
```

### 2.2 Feign Client Pattern (Example)

```java
@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {

    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getActiveBookingCount(@PathVariable Long userId);

    @GetMapping("/api/bookings/user/{userId}/completed-count")
    long getCompletedBookingCount(@PathVariable Long userId);
}
```

In `application.yml`, add each service to the one that requires it:

```yaml
feign:
  user-service:
    url: http://user-service:8080
  provider-service:
    url: http://provider-service:8080
  booking-service:
    url: http://booking-service:8080
  calendar-service:
    url: http://calendar-service:8080
  invoice-service:
    url: http://invoice-service:8080
```

### 2.3 Correlation ID Propagation

Every service must forward `X-Correlation-ID` on all outgoing Feign calls:

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

### 2.4 Error Handling

Wrap every Feign call in try-catch. Never let a downstream failure crash the calling service, for example:

```java
try {
    BookingSummaryDTO summary = bookingServiceClient.getUserBookingSummary(userId);
    return buildDTO(user, summary);
} catch (FeignException.NotFound e) {
    return buildDTO(user, BookingSummaryDTO.empty());
} catch (FeignException e) {
    log.warn("booking-service unavailable for user {}: {}", userId, e.getMessage());
    throw new ServiceUnavailableException("Booking service temporarily unavailable");
}
```

---

### 2.5 RabbitMQ Dependency

Add to every service that publishes or consumes events:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2.6 RabbitMQ Connection Configuration

Add to every service's `application.yml`:

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
```

### 2.7 Topology — What Each Service Must Declare

Each service declares its own RabbitMQ topology as Spring `@Bean`s in a `@Configuration` class. The responsibilities are split:

- **Producer service** declares only the `TopicExchange` it publishes to.
- **Consumer service** declares the `Queue`, the `DLQ`, another `TopicExchange` reference (same name — Spring deduplicates), and the `Binding` that connects them.

Every consumer queue must have a dead-letter queue. The DLQ is wired by declaring the consumer queue with the `x-dead-letter-exchange` and `x-dead-letter-routing-key` arguments pointing at a separate dead-letter exchange + DLQ. Combined with `default-requeue-rejected: false` (§2.6), this means: when a listener method throws and Spring's retry exhausts (`max-attempts: 3`), the message is automatically routed to the DLQ — no manual `basicAck`/`basicNack` code in the consumer.

The exchange type for all Booking events is **`TopicExchange`**. This allows routing key wildcards so future events can be added without declaring a new exchange.

### 2.8 Event Payload Records

Event payloads are plain Java `record` classes, serialized to JSON by Jackson. They live in an `events` package inside the shared `contracts` module:

```java
// booking-service
public record BookingPlacedEvent(Long bookingId, Long userId, Long providerId) {}
public record BookingCompletedEvent(Long bookingId, Long userId, Long providerId, Double totalPrice) {}
public record BookingCancelledEvent(Long bookingId, Long userId, Long providerId, String reason) {}

// provider-service
public record ProviderStatusChangedEvent(Long providerId, String oldStatus, String newStatus) {}
public record ProviderRatedEvent(Long providerId, Long bookingId, Double rating, Long userId) {}
public record ProviderCertificationVerifiedEvent(Long providerId, Long certificationId, Long verifiedBy) {}

// invoice-service
public record PaymentInitiatedEvent(Long invoiceId, Long bookingId, Double amount) {}
public record PaymentCompletedEvent(Long invoiceId, Long bookingId, Double amount) {}
public record PaymentFailedEvent(Long invoiceId, Long bookingId, String reason) {}
public record PaymentRefundedEvent(Long invoiceId, Long bookingId, Double refundAmount) {}

// user-service
public record UserRegisteredEvent(Long userId, String email, String role) {}
public record UserDeactivatedEvent(Long userId) {}

// calendar-service
public record SlotReservedEvent(Long slotId, Long providerId, Long bookingId) {}
public record SlotReleasedEvent(Long slotId, Long providerId, Long bookingId) {}
```

### 2.9 Full Event Map (Booking)

| Producer         | Exchange            | Routing key                     | Payload record                       | Consumers                                                       |
| ---------------- | ------------------- | ------------------------------- | ------------------------------------ | --------------------------------------------------------------- |
| user-service     | `user.events`       | `user.registered`               | `UserRegisteredEvent`                | *(observability only)*                                          |
| user-service     | `user.events`       | `user.deactivated`              | `UserDeactivatedEvent`               | *(observability only)*                                          |
| provider-service | `provider.events`   | `provider.status-changed`       | `ProviderStatusChangedEvent`         | *(observability only)*                                          |
| provider-service | `provider.events`   | `provider.rated`                | `ProviderRatedEvent`                 | *(observability only)*                                          |
| provider-service | `provider.events`   | `provider.certification.verified` | `ProviderCertificationVerifiedEvent` | *(observability only)*                                          |
| booking-service  | `booking.events`    | `booking.placed`                | `BookingPlacedEvent`                 | provider-service, calendar-service                              |
| booking-service  | `booking.events`    | `booking.completed`             | `BookingCompletedEvent`              | user-service, provider-service, calendar-service, invoice-service |
| booking-service  | `booking.events`    | `booking.cancelled`             | `BookingCancelledEvent`              | user-service, provider-service, calendar-service, invoice-service |
| calendar-service | `calendar.events`   | `slot.reserved`                 | `SlotReservedEvent`                  | *(observability only)*                                          |
| calendar-service | `calendar.events`   | `slot.released`                 | `SlotReleasedEvent`                  | *(observability only)*                                          |
| invoice-service  | `payment.events`    | `payment.initiated`             | `PaymentInitiatedEvent`              | booking-service                                                 |
| invoice-service  | `payment.events`    | `payment.completed`             | `PaymentCompletedEvent`              | booking-service                                                 |
| invoice-service  | `payment.events`    | `payment.failed`                | `PaymentFailedEvent`                 | booking-service                                                 |
| invoice-service  | `payment.events`    | `payment.refunded`              | `PaymentRefundedEvent`               | booking-service                                                 |

> **Note on observability-only events:** events marked *(observability only)* are published for audit trail / monitoring purposes (consumed by Loki via the JSON log pipeline in §11.1, not by another service's RabbitMQ listener). The per-service "RabbitMQ Consumes" tables in §3–§7 list **only the saga + business-flow consumers** that mutate per-service state. The 7 events above (`user.registered`, `user.deactivated`, `provider.status-changed`, `provider.rated`, `provider.certification.verified`, `slot.reserved`, `slot.released`) are emitted but no service consumes them as RabbitMQ messages — Loki picks them up from the publisher's logback emission.

---

## Section 3 — User Service Refactoring (S1)

### New Endpoints S1 Must Expose

No new external endpoints — user-service already exposes `GET /api/users/{id}` (M1 CRUD). Downstream services call this existing endpoint.

### Features That Require Feign Calls

---

#### [S1-F3] Get User Booking Summary

**Branch:** `feat/M3/user/S1-F3/<studentID>`  
**Endpoint:** `GET /api/users/{id}/booking-summary`

**M1 implementation:** Direct native SQL JOIN on the shared `bookings` table using `user_id` to compute total/completed/cancelled counts and total spent.

**M3 change:** Replace the SQL JOIN with a Feign call to booking-service, so that the interface would be:

```java
@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {
    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable Long userId);
}
```

`BookingSummaryDTO` returned by booking-service: `{totalBookings, completedBookings, cancelledBookings, totalSpent, averageBookingPrice}`

user-service calls this and merges it with the local `User` data to build `UserBookingSummaryDTO`.

**Test scenario:**

1. (setup) In user-postgres: create User ID=1. In booking-postgres: create 5 bookings for userId=1 — 3 COMPLETED (totalPrices 200, 350, 450), 1 CANCELLED, 1 REQUESTED.
2. (action) `GET /api/users/1/booking-summary` with valid Bearer token.
3. (expect) 200 — `totalBookings=5, completedBookings=3, cancelledBookings=1, totalSpent=1000.00, averageBookingPrice=333.33`.
4. (verify) No direct JDBC connection from user-postgres to booking-postgres. The booking counts come from a Feign HTTP call.

---

#### [S1-F4] Deactivate User Account

**Branch:** `feat/M3/user/S1-F4/<studentID>`  
**Endpoint:** `PUT /api/users/{id}/deactivate`

**M1 implementation:** `SELECT COUNT(*) FROM bookings WHERE user_id = ? AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS')` directly on the shared database.

**M3 change:** Replace with a Feign call to booking-service `GET /api/bookings/user/{userId}/active-count` → returns `int`.

If the returned count > 0, throw 400 ("User has active bookings"). Otherwise set status = DEACTIVATED and save.

After deactivation: publish `user.deactivated` RabbitMQ event (see §2.9).

**Test scenario:**

1. (setup) User ID=1 in user-postgres. Booking in booking-postgres: userId=1, status=REQUESTED.
2. (action) `PUT /api/users/1/deactivate` → Feign → booking-service returns active-count=1.
3. (expect) 400 — cannot deactivate user with active bookings.
4. (setup) Update the booking status to COMPLETED in booking-postgres.
5. (action) `PUT /api/users/1/deactivate` → Feign returns active-count=0.
6. (expect) 200 — user status = DEACTIVATED in user-postgres. RabbitMQ `user.deactivated` event published.

---

#### [S1-F6] Top Clients by Spending

**Branch:** `feat/M3/user/S1-F6/<studentID>`  
**Endpoint:** `GET /api/users/reports/top-clients?startDate={date}&endDate={date}&limit={n}`

**M1 implementation:** Native SQL JOIN of `users` with `invoices` (or `bookings`) grouped by user, ordered by `SUM(amount) DESC`, against the shared database.

**M3 change:** user-service cannot JOIN the `invoices` table (it lives in invoice-postgres). Instead:

1. Fetch all users from user-postgres.
2. For each user, call Feign → `GET /api/invoices/user/{userId}/total` on invoice-service → returns `BigDecimal` (total COMPLETED invoice amount for this user in the date range).
3. Sort users by total descending, take first `limit`.
4. Build and return `List<TopClientDTO>`.

> **Note on date filtering:** Pass `startDate` and `endDate` as query params to the invoice-service endpoint so it filters server-side rather than fetching all invoices, so that the interface would be

```java
@FeignClient(name = "invoice-service", url = "${feign.invoice-service.url}")
public interface InvoiceServiceClient {
    @GetMapping("/api/invoices/user/{userId}/total")
    BigDecimal getUserInvoiceTotal(
        @PathVariable Long userId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
```

**Test scenario:**

1. (setup) 3 users in user-postgres. In invoice-postgres: User A = 800 total, User B = 2500 total, User C = 300 total, all within March 2026.
2. (action) `GET /api/users/reports/top-clients?startDate=2026-03-01&endDate=2026-03-31&limit=2`.
3. (expect) 200 — `[{userId: B, totalSpent: 2500, bookingCount: ...}, {userId: A, totalSpent: 800, bookingCount: ...}]`.
4. (verify) user-service made Feign calls to invoice-service for each user's total. No direct query on `invoices` table.

---

#### [S1-F9] Find Users by Language Preference with Minimum Bookings

**Branch:** `feat/M3/user/S1-F9/<studentID>`  
**Endpoint:** `GET /api/users/preferences/language?lang={lang}&minBookings={n}`

**M1 implementation:** JSONB query on `users` table + subquery counting `bookings` rows by `user_id` with status=COMPLETED.

**M3 change:**

1. Query user-postgres for users whose `preferences->>'language'` matches the given value.
2. For each matching user, call Feign → `GET /api/bookings/user/{userId}/completed-count` → returns `long`.
3. Keep only users whose returned count ≥ `minBookings`.

**Test scenario:**

1. (setup) 3 users in user-postgres: User A and User B have `language=ar`, User C has `language=en`. In booking-postgres: User A has 5 COMPLETED bookings, User B has 2 COMPLETED bookings, User C has 10 COMPLETED bookings.
2. (action) `GET /api/users/preferences/language?lang=ar&minBookings=3`.
3. (expect) 200 — only User A returned (User B has 2 completed bookings, below threshold).
4. (verify) Feign call made to booking-service for each candidate user.

---

### Features Verified as NOT Cross-Service (Booking-Specific)

**S1-F1 (Search Users with Filters)** — Local query on `users` (JSONB on `preferences`). **No M3 change.**

**S1-F2 (Update User Preferences — JSONB)** — Local UPDATE on `users`. **No M3 change.**

**S1-F5 (Filter Users by Preference — JSONB query)** — Local query on `users`. **No M3 change.**

**S1-F7 (Set Default Saved Address)** — Local transactional update across `users` + `saved_addresses` (both intra-service). **No M3 change.**

**S1-F8 (Get User Profile with Addresses)** — Local read of `users + saved_addresses`. **No M3 change.**

**S1-F10 (Register User — M2)** — Local insert + publishes `user.registered` (this is the M2 → M3 retrofit, not a cross-service Feign change). **No M3 Feign change beyond what M2 already specifies.**

**S1-F11 (Login — M2)** — Local validation + JWT issuance. **No M3 change.**

**S1-F12 (Get User Activity Feed — M2)** — Reads MongoDB `auth_events` (locally owned by user-service). **No M3 change.**

---

### RabbitMQ: S1 Publishes

| Routing key        | Exchange      | Payload                 | When                                               |
| ------------------ | ------------- | ----------------------- | -------------------------------------------------- |
| `user.registered`  | `user.events` | `{userId, email, role}` | After successful user registration (S1-F10) — *(observability only — see §2.9)* |
| `user.deactivated` | `user.events` | `{userId}`              | After S1-F4 successfully sets DEACTIVATED — *(observability only — see §2.9)* |

### RabbitMQ: S1 Consumes

| Routing key          | From exchange    | Action                                                                                |
| -------------------- | ---------------- | ------------------------------------------------------------------------------------- |
| `booking.completed`  | `booking.events` | Update user's booking stats (increment total bookings, total spent) in user-postgres  |
| `booking.cancelled`  | `booking.events` | Reverse user's booking stats (decrement total bookings, subtract amount) in user-postgres |

Queue declaration: `user.booking.saga-listener` with DLQ `user.booking.saga-listener.dlq`.

### S1 Deliverables

- [ ] Remove any cross-service `@ManyToOne` on User / SavedAddress entities (M1 already kept these intra-service)
- [ ] `feign.booking-service.url` and `feign.invoice-service.url` in `application.yml`
- [ ] `BookingServiceClient` Feign interface with `getUserBookingSummary`, `getActiveBookingCount`, `getCompletedBookingCount`
- [ ] `InvoiceServiceClient` Feign interface with `getUserInvoiceTotal`
- [ ] S1-F3 refactored to use Feign → booking-service
- [ ] S1-F4 refactored to use Feign → booking-service; publishes `user.deactivated` after success
- [ ] S1-F6 refactored to use Feign → invoice-service per user
- [ ] S1-F9 refactored to use Feign → booking-service per matching user
- [ ] RabbitMQ `user.events` TopicExchange declared
- [ ] `user.registered` published on registration
- [ ] `user.deactivated` published on S1-F4
- [ ] Consumer for `booking.completed` and `booking.cancelled` with auto ACK + DLQ via `x-dead-letter-exchange`
- [ ] `logback-spring.xml` with Loki4J appender (see §11)

---

## Section 4 — Provider Service Refactoring (S2)

### New Endpoints S2 Must Expose

These endpoints are called by other services via Feign. They must exist before S1, S3, S4, S5 SYNC branches are merged.

| Endpoint                                | Called by             | Returns                | Description                                                                                                                      |
| --------------------------------------- | --------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/providers/{id}`               | S3, S4, S5            | `ProviderDTO`          | Already exists (M1 CRUD). Verify it returns `id, name, phone, specialty, status, rating, totalRatings, serviceDetails`.          |
| `GET /api/providers/{id}/availability`  | S3 (S3-F2 pre-check)  | `{"status": String}`   | Convenience endpoint returning the provider's current `status` value. 404 if provider not found.                                 |

---

#### [S2-F3] Get Provider Earnings Summary

**Branch:** `feat/M3/provider/S2-F3/<studentID>`  
**Endpoint:** `GET /api/providers/{id}/earnings?startDate={date}&endDate={date}`

**M1 implementation:** Native SQL aggregation on the shared `bookings` table for completed bookings assigned to this provider.

**M3 change:** Replace with Feign → booking-service.

```java
@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {
    @GetMapping("/api/bookings/provider/{providerId}/summary")
    ProviderBookingSummaryDTO getProviderBookingSummary(
        @PathVariable Long providerId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
```

`ProviderBookingSummaryDTO` from booking-service: `{totalBookings, totalEarnings, averageBookingPrice}`

provider-service merges this with the local `Provider` entity to build `ProviderEarningsDTO`.

**Test scenario:**

1. (setup) Provider ID=1 in provider-postgres. 5 COMPLETED bookings in booking-postgres for providerId=1 in March 2026 with totalPrices 200, 250, 300, 350, 400.
2. (action) `GET /api/providers/1/earnings?startDate=2026-03-01&endDate=2026-03-31`.
3. (expect) 200 — `totalBookings=5, totalEarnings=1500.00, averageBookingPrice=300.00`.
4. (verify) No direct JOIN on booking-postgres from provider-service.

---

#### [S2-F4] Toggle Provider Availability

**Branch:** `feat/M3/provider/S2-F4/<studentID>`  
**Endpoint:** `PUT /api/providers/{id}/availability`  
**Request body:** `{"status": "OFFLINE" | "AVAILABLE" | "BUSY"}`

**M1 implementation:** When transitioning to OFFLINE, runs `SELECT COUNT(*) FROM bookings WHERE provider_id = ? AND status IN ('CONFIRMED','IN_PROGRESS')` on the shared database to block the change if any active booking exists.

**M3 change:** Replace the SQL count with Feign → booking-service `GET /api/bookings/provider/{providerId}/active-count`. If the count > 0 and the requested status is OFFLINE, throw 400. Otherwise update the local provider row and save.

After the local update, publish `provider.status-changed` to the `provider.events` exchange.

**Test scenario:**

1. (setup) Provider ID=1 in provider-postgres (status=BUSY). Booking in booking-postgres: providerId=1, status=IN_PROGRESS.
2. (action) `PUT /api/providers/1/availability` body `{"status": "OFFLINE"}` → Feign returns active-count=1.
3. (expect) 400 — cannot go OFFLINE with active bookings.
4. (setup) Update the booking status to COMPLETED in booking-postgres.
5. (action) `PUT /api/providers/1/availability` body `{"status": "OFFLINE"}` → Feign returns active-count=0.
6. (expect) 200 — provider status=OFFLINE. `provider.status-changed` event published.

---

#### [S2-F7] Rate a Provider After Booking

**Branch:** `feat/M3/provider/S2-F7/<studentID>`  
**Endpoint:** `POST /api/providers/{id}/rate`  
**Request body:** `{"bookingId": Long, "rating": Double}`

**M1 implementation:** `SELECT * FROM bookings WHERE id = ? AND provider_id = ? AND status = 'COMPLETED'` on the shared database.

**M3 change:** Replace with Feign → booking-service `GET /api/bookings/{bookingId}` (reuses existing M1 CRUD endpoint). Validate the returned booking:

- Exists (Feign returns 404 → throw 404)
- `providerId` matches the provider being rated (mismatch → throw 400)
- `status` IN (`COMPLETING`, `PAYMENT_PENDING`, `PAID`) — the appointment is finished and the saga has progressed past completion. Wrong status → throw 400. (`COMPLETED` from a legacy M1 flow also accepted; `REFUNDED` and `PAYMENT_FAILED` are excluded — refunded appointments do not get rated.)
- `rating` is between 1 and 5 (invalid → throw 400)

If all checks pass, recompute the running average on `Provider.rating` + `Provider.totalRatings` and save. Publish `provider.rated` RabbitMQ event with `{providerId, bookingId, rating, userId}` (the userId is read from the booking DTO).

**Test scenario:**

1. (setup) Provider ID=1 in provider-postgres (rating=0.0, totalRatings=0). Booking ID=10 in booking-postgres: providerId=1, userId=7, status=PAID (saga-completed).
2. (action) `POST /api/providers/1/rate` body `{"bookingId": 10, "rating": 5}`.
3. (expect) 200 — provider rating=5.0, totalRatings=1. `provider.rated` event published.
4. (action) Same call with a second PAID booking and `rating=3` → 200, rating=4.0, totalRatings=2.
5. (action) Booking ID=11 with status=PAYMENT_PENDING → 200 (rating allowed mid-saga).
6. (action) Booking ID=12 with status=REFUNDED → Feign returns booking → 400 (refunded appointments not rateable).
7. (action) `POST /api/providers/1/rate` body `{"bookingId": 99, "rating": 4.0}` → Feign returns 404 → throw 404.
8. (action) `POST /api/providers/1/rate` body `{"bookingId": 10, "rating": 4.0}` for a booking whose providerId is different → 400.
9. (action) `POST /api/providers/1/rate` body `{"bookingId": 10, "rating": 6}` → 400 (out of range).

---

#### [S2-F8] Verify Provider Certification

**Branch:** `feat/M3/provider/S2-F8/<studentID>`  
**Endpoint:** `PUT /api/providers/{providerId}/certifications/{certificationId}/verify`  
**Request body:** `{"verifiedBy": Long}`

**M1 implementation:** Local validation on provider + certification; then `SELECT role FROM users WHERE id = ?` against the shared `users` table to confirm the `verifiedBy` user has role ADMIN.

**M3 change:** Replace the user lookup with a Feign call to user-service.

```java
@FeignClient(name = "user-service", url = "${feign.user-service.url}")
public interface UserServiceClient {
    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable Long id);
}
```

After the Feign call:
- 404 from Feign → throw 403 ("verifier user not found")
- `role` ≠ `ADMIN` → throw 403 ("verifier is not an admin")

If admin verification passes and the certification is unexpired and belongs to the provider, set `verified = true`, update the certification's JSONB metadata with `verifiedAt` and `verifiedBy`, save. Publish `provider.certification.verified` to `provider.events`.

**Test scenario:**

1. (setup) Provider ID=1 with ProviderCertification ID=10 (type=LICENSE, expiryDate=2027-12-31, verified=false). User ID=3 in user-postgres with role=ADMIN.
2. (action) `PUT /api/providers/1/certifications/10/verify` body `{"verifiedBy": 3}`.
3. (expect) 200 — certification verified=true. JSONB metadata contains `verifiedAt` + `verifiedBy=3`. `provider.certification.verified` published.
4. (action) Same call with `verifiedBy=99` (no such user) → Feign 404 → throw 403.
5. (action) `verifiedBy=2` where user 2's role=CLIENT → throw 403.
6. (action) Certification with `expiryDate` in the past → 400 (no Feign call needed; local validation fails first).

---

#### [S2-F12] Get Provider Performance Dashboard *(M2)*

**Branch:** `feat/M3/provider/S2-F12/<studentID>`  
**Endpoint:** `GET /api/providers/{id}/dashboard`

**M2 implementation:** Aggregates `totalBookings`, `totalRevenue`, `averageBookingValue` by joining `bookings JOIN invoices ON invoices.booking_id = bookings.id WHERE bookings.provider_id = ?` on the shared database. Reads `rating` and `totalRatings` from the local provider row, plus computes `utilizationRate` from `time_slots` for the current month.

**M3 change:** The cross-service aggregation becomes a Feign call to booking-service — reuse the same `GET /api/bookings/provider/{providerId}/summary` endpoint from S2-F3 (the booking-service in turn aggregates the booking + invoice data through its own consumer-maintained projection — see §5 events). The provider's own `rating` and `totalRatings` are still read from the local `providers` table. The `utilizationRate` requires a Feign call to calendar-service:

```java
@FeignClient(name = "calendar-service", url = "${feign.calendar-service.url}")
public interface CalendarServiceClient {
    @GetMapping("/api/timeslots/provider/{providerId}/utilization")
    ProviderUtilizationDTO getProviderUtilization(
        @PathVariable Long providerId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
```

(This `GET /api/timeslots/provider/{providerId}/utilization` endpoint already exists in M1 as the public S4-F8 endpoint — it now becomes a Feign-callable endpoint as well.)

**Test scenario:**

1. (setup) Provider ID=5 in provider-postgres (specialty="Dentist", rating=4.5, totalRatings=100). In booking-postgres: 5 COMPLETED bookings for providerId=5 with invoice totals 100, 200, 150, 300, 250 in invoice-postgres. In calendar-postgres: 20 time slots for providerId=5 in the current month, 12 of them booked.
2. (action) `GET /api/providers/5/dashboard` with valid Bearer token.
3. (expect) 200 — `totalBookings=5, totalRevenue=1000, averageBookingValue=200, averageRating=4.5, utilizationRate=0.6`.
4. (action) `GET /api/providers/999/dashboard` → 404.

---

### Features Verified as NOT Cross-Service (Booking-Specific)

**S2-F1 (Search Providers by Status and Rating Range)** — Reads only the local `providers` table. **No M3 change.**

**S2-F2 (Update Service Details — JSONB partial update)** — Reads/writes only the local `providers` row. **No M3 change.**

**S2-F5 (Filter Providers by Pricing Tier)** — Local JSONB query on `providers`. **No M3 change.**

**S2-F6 (Top Rated Providers Report)** — Response DTO is `{providerId, name, rating, totalBookings}`. The `totalBookings` field is sourced from the local `Provider.totalRatings` column (which the M1 spec uses as a proxy for completed bookings). If your team derived `totalBookings` from a JOIN on `bookings`, refactor it to call `GET /api/bookings/provider/{providerId}/completed-count` per candidate. If `totalBookings` came from `totalRatings`, no M3 change is needed.

**S2-F9 (Get Providers with Expired Certifications)** — Local query on `providers JOIN provider_certifications` (both intra-service). **No M3 change.**

**S2-F10 (Full-Text Provider Search)** — Reads Elasticsearch + the local provider row for enrichment. **No M3 change.**

**S2-F11 (Index Provider for Search)** — Reads the local provider row, writes ES, logs to MongoDB. **No M3 change.**

### RabbitMQ: S2 Publishes

| Routing key                       | Exchange           | Payload                                          | When                                                                |
| --------------------------------- | ------------------ | ------------------------------------------------ | ------------------------------------------------------------------- |
| `provider.status-changed`         | `provider.events`  | `{providerId, oldStatus, newStatus}`             | After S2-F4 updates provider status — *(observability only — see §2.9)* |
| `provider.rated`                  | `provider.events`  | `{providerId, bookingId, rating, userId}`        | After S2-F7 rating submission — *(observability only — see §2.9)*        |
| `provider.certification.verified` | `provider.events`  | `{providerId, certificationId, verifiedBy}`      | After S2-F8 successfully verifies — *(observability only — see §2.9)*    |

### RabbitMQ: S2 Consumes

| Routing key          | From exchange     | Action                                                                                                                                                                                          |
| -------------------- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `booking.placed`     | `booking.events`  | Set the assigned provider's status to BUSY in provider-postgres (replaces the M1 direct SQL inside S3-F2)                                                                                       |
| `booking.completed`  | `booking.events`  | Set the assigned provider's status back to AVAILABLE; increment provider statistics (total completed bookings) in provider-postgres                                                              |
| `booking.cancelled`  | `booking.events`  | Set the assigned provider's status back to AVAILABLE in provider-postgres (replaces the M1 native-SQL update inside S3-F7); reverse provider statistics if previously incremented                |

Queue declaration: `provider.booking.saga-listener` with DLQ `provider.booking.saga-listener.dlq`.

### S2 Deliverables

- [ ] `GET /api/providers/{id}/availability` endpoint implemented in provider-service
- [ ] `feign.booking-service.url`, `feign.user-service.url`, and `feign.calendar-service.url` in provider-service `application.yml`
- [ ] `BookingServiceClient` Feign interface with `getProviderBookingSummary`, `getActiveBookingCount`, `getBooking`
- [ ] `UserServiceClient` Feign interface with `getUser`
- [ ] `CalendarServiceClient` Feign interface with `getProviderUtilization`
- [ ] S2-F3 refactored to use Feign → booking-service
- [ ] S2-F4 refactored to use Feign → booking-service; publishes `provider.status-changed`
- [ ] S2-F7 refactored to use Feign → booking-service for booking validation; publishes `provider.rated`
- [ ] S2-F8 refactored to use Feign → user-service for ADMIN verification; publishes `provider.certification.verified`
- [ ] S2-F12 refactored to use Feign → booking-service + calendar-service for dashboard aggregation
- [ ] RabbitMQ `provider.events` TopicExchange declared
- [ ] Consumer for `booking.placed`, `booking.completed`, `booking.cancelled` with auto ACK + DLQ via `x-dead-letter-exchange`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 5 — Booking Service Refactoring (S3)

### New Endpoints S3 Must Expose

These are called by S1, S2, S4, S5 via Feign. All must be implemented before the EVENTS branches merge.

| Endpoint                                              | Called by                       | Returns                       | Description                                                                                                                          |
| ----------------------------------------------------- | ------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `GET /api/bookings/user/{userId}/summary`             | S1 (S1-F3)                      | `BookingSummaryDTO`           | `{totalBookings, completedBookings, cancelledBookings, totalSpent, averageBookingPrice}`                                             |
| `GET /api/bookings/user/{userId}/active-count`        | S1 (S1-F4)                      | `int`                         | Count of bookings with status IN (REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETING, PAYMENT_PENDING)                                    |
| `GET /api/bookings/user/{userId}/completed-count`     | S1 (S1-F9)                      | `long`                        | Count of bookings for this user with status IN (COMPLETING, PAYMENT_PENDING, PAID, REFUNDED) — every appointment that finished, regardless of payment outcome |
| `GET /api/bookings/provider/{providerId}/summary`     | S2 (S2-F3, S2-F12)              | `ProviderBookingSummaryDTO`   | `{totalBookings, totalEarnings, averageBookingPrice}` for bookings in PAID status within an optional date range                       |
| `GET /api/bookings/provider/{providerId}/active-count`| S2 (S2-F4)                      | `int`                         | Count of bookings with status IN (CONFIRMED, IN_PROGRESS, COMPLETING, PAYMENT_PENDING)                                                |
| `GET /api/bookings/provider/{providerId}/completed-count`| S2 (S2-F6)                   | `long`                        | Count of bookings for this provider with status IN (COMPLETING, PAYMENT_PENDING, PAID, REFUNDED) — used by the top-rated providers report |
| `GET /api/bookings/{bookingId}`                       | S2 (S2-F7), S5 (S5-F4), S4      | `BookingDTO`                  | Already exists (M1 CRUD). Verify it returns `id, userId, providerId, status, totalPrice, appointmentDate, startTime, endTime, completedAt, metadata`. |

---

#### [S3-F2] Assign Provider to Booking

**Branch:** `feat/M3/booking/S3-F2/<studentID>`  
**Endpoint:** `PUT /api/bookings/{bookingId}/assign?providerId={providerId}`

**M1 implementation:** `SELECT * FROM providers WHERE id = ? AND status = 'AVAILABLE'` on the shared database, then `UPDATE providers SET status = 'BUSY' WHERE id = ?`.

**M3 change:** Replace the read with Feign → provider-service `GET /api/providers/{providerId}`. Validate:

- 404 from Feign → throw 404 ("Provider not found")
- `status` ≠ `AVAILABLE` → throw 400 ("Provider is not available")

If valid: set `booking.providerId = providerId`, set booking status = CONFIRMED, save. **Do not** update the provider's status directly. Instead, publish `booking.placed` to `booking.events`. The provider-service consumer of `booking.placed` flips the provider's status to BUSY in its own database (see §4 RabbitMQ consumers).

```java
@FeignClient(name = "provider-service", url = "${feign.provider-service.url}")
public interface ProviderServiceClient {
    @GetMapping("/api/providers/{id}")
    ProviderDTO getProvider(@PathVariable Long id);

    @GetMapping("/api/providers/{id}/availability")
    ProviderAvailabilityDTO getProviderAvailability(@PathVariable Long id);
}
```

**Test scenario:**

1. (setup) REQUESTED booking ID=1 in booking-postgres (no provider assigned). Provider ID=10 in provider-postgres (specialty="Dentist", status=AVAILABLE).
2. (action) `PUT /api/bookings/1/assign?providerId=10` → Feign returns provider with status=AVAILABLE.
3. (expect) 200 — booking status=CONFIRMED, providerId=10. `booking.placed` event published. After event processing: provider-postgres has providerId=10, status=BUSY.
4. (action) `PUT /api/bookings/1/assign?providerId=10` → booking is now CONFIRMED, not REQUESTED → 400.
5. (action) Use a provider with status=BUSY → Feign returns BUSY → 400.
6. (action) Use providerId=999 → Feign throws 404 → 404.

---

#### [S3-F3] Get Booking Price Estimate

**Branch:** `feat/M3/booking/S3-F3/<studentID>`  
**Endpoint:** `POST /api/bookings/estimate`  
**Body:** `{providerId, appointmentDate, services: [{serviceName, duration}]}`

**M1 implementation:** Reads only the local `bookings` table (counting active bookings on the same provider/date for the demand multiplier). Already single-service. **No M3 change required.**

> **Verification:** the M1 SQL is `SELECT COUNT(*) FROM bookings WHERE provider_id = ? AND appointment_date = ? AND status IN (...)`. The `bookings` table is owned by booking-service. No cross-service data is read (the `providerId` in the request body is used as a literal — no validation against `providers` is performed in M1, so no Feign call is added in M3). No Feign call is needed.

> **Test scenario:** unchanged from M1/M2 — there is no M3 behavior change to verify. Use the existing M1/M2 test scenarios for this feature.

---

> **S3-F4 and S3-F7** are RabbitMQ-based changes, not Feign. They are fully described in **Section 8 (Booking Lifecycle Saga & Cancellation Cascade)**.

---

#### [S3-F11] Record User-Provider Booking Pattern *(M2)*

**Branch:** `feat/M3/booking/S3-F11/<studentID>`  
**Endpoint:** `POST /api/bookings/{bookingId}/record-interaction`

**M2 implementation:** Direct SQL on shared database: `SELECT name FROM users WHERE id = ?` and `SELECT name, specialty FROM providers WHERE id = ?` to fetch the user's name and the provider's name + specialty for the Neo4j node creation.

**M3 change:** Replace both SQL queries with Feign calls.

```java
// Get user details from user-service
UserDTO user = userServiceClient.getUser(booking.getUserId());

// Get provider details from provider-service
ProviderDTO provider = providerServiceClient.getProvider(booking.getProviderId());
String specialty = provider.getSpecialty();

// Then proceed with Neo4j graph write as in M2
// (UserNode, ProviderNode, BOOKED relationship with bookingCount + lastBookingDate + idempotency marker)
```

The rest of the feature (Neo4j idempotency via the `recorded_booking_ids` collection on the relationship, BOOKED increment, MongoDB INTERACTION_RECORDED event logging) is unchanged from M2.

**Test scenario:**

1. (setup) User ID=1 in user-postgres (name="Sara Kamal"). Provider ID=5 in provider-postgres (name="Dr. Omar Nasr", specialty="Dentist"). Completed booking ID=10 in booking-postgres: userId=1, providerId=5, status=COMPLETED.
2. (action) `POST /api/bookings/10/record-interaction`.
3. (expect) 200. Neo4j has `(UserNode {userId:1, name:"Sara Kamal"})-[:BOOKED {bookingCount:1}]->(ProviderNode {providerId:5, name:"Dr. Omar Nasr", specialty:"Dentist"})`. MongoDB `booking_events` has an INTERACTION_RECORDED document.
4. (action) Repeat the same `POST /api/bookings/10/record-interaction` → 200, but `bookingCount` stays at 1 (idempotency — the `recorded_booking_ids` set already contains 10).
5. (verify) Feign calls made to user-service and provider-service. No direct SQL on user-postgres or provider-postgres from booking-service.

---

#### [S3-F12] Get Provider Recommendations for User *(M2)*

**Branch:** `feat/M3/booking/S3-F12/<studentID>`  
**Endpoint:** `GET /api/bookings/recommendations?userId={id}&limit={n}`

**M2 implementation:** After traversing the Neo4j graph for candidate provider IDs, runs `SELECT name, specialty FROM providers WHERE id IN (?)` against the shared `providers` table to enrich the recommendations.

**M3 change:** Replace the SQL enrichment with Feign calls to provider-service.

```java
// Verify the requesting user exists
UserDTO user = userServiceClient.getUser(userId);

// For each candidate providerId from Neo4j graph traversal:
ProviderDTO provider = providerServiceClient.getProvider(candidateId);
recommendations.add(new ProviderRecommendationDTO(provider.getId(), provider.getName(), provider.getSpecialty(), score));
```

The Neo4j collaborative-filtering traversal logic (find users who booked with the same providers, then providers those users booked with that this user has not), the limit, and the cache TTL are unchanged from M2.

**Test scenario:**

1. (setup) Users U1, U2, U3 in user-postgres. Providers P1, P2, P3, P4 in provider-postgres (P4 specialty="Tutor"). Neo4j has interactions: U1→P1, U1→P2; U2→P1, U2→P3; U3→P2, U3→P4.
2. (action) `GET /api/bookings/recommendations?userId={U1.id}&limit=5` with U1's own token.
3. (expect) 200 — P3 recommended (because U2 booked with P1 and P3, score=1) and P4 recommended (because U3 booked with P2 and P4, score=1). Each result enriched with `name` and `specialty` from provider-service. NOT including P1 or P2 (already booked).
4. (verify) Feign call to user-service for ownership check (404 path). Feign calls to provider-service for each candidate provider. No SQL on user-postgres or provider-postgres from booking-service.

---

#### [S3-F10] Get Booking Analytics Dashboard *(M2)*

**Branch:** `feat/M3/booking/S3-F10/<studentID>`  
**Endpoint:** `GET /api/bookings/analytics/dashboard?startDate={date}&endDate={date}`

**M2 implementation:** Aggregates from `bookings` table (joined with `invoices` for revenue) — `totalRevenue` is computed as `sum(invoices.amount)` across COMPLETED invoices for completed bookings in the date range. The JOIN crosses the booking-service / invoice-service boundary on the shared M2 database.

**M3 change:** booking-service cannot JOIN the `invoices` table (it lives in invoice-postgres). Replace the cross-service revenue computation with a Feign batch call:

1. Local query on booking-postgres: aggregate `totalBookings`, `bookingsByStatus` (count map), `completionRate` for the date range. The "completed" set used for `completionRate` is `status IN (COMPLETING, PAYMENT_PENDING, PAID, REFUNDED)` divided by `totalBookings`.
2. Collect the booking IDs whose status is in the saga-completed set above.
3. Feign → invoice-service `POST /api/invoices/by-bookings` with body `{"bookingIds": [...]}` → receives a `Map<Long, InvoiceAmountDTO>` keyed by bookingId. Sum the `amount` values to compute `totalRevenue`. Bookings without a COMPLETED invoice contribute 0.
4. Compute `averageBookingValue = totalRevenue / count(bookings with COMPLETED invoice)`. If the denominator is 0, return 0.
5. Build and return `BookingAnalyticsDashboardDTO` with `totalBookings`, `totalRevenue`, `averageBookingValue`, `completionRate`, `bookingsByStatus`. Cache for 10 minutes (per M2). Log `ANALYTICS_VIEWED` to MongoDB `booking_events` on every invocation (M2 unchanged).

```java
@FeignClient(name = "invoice-service", url = "${feign.invoice-service.url}")
public interface InvoiceServiceClient {
    @PostMapping("/api/invoices/by-bookings")
    Map<Long, InvoiceAmountDTO> getInvoiceAmountsByBookings(@RequestBody InvoiceAmountsRequest body);
}

public record InvoiceAmountsRequest(List<Long> bookingIds) {}
public record InvoiceAmountDTO(Long bookingId, BigDecimal amount) {}
```

> **Single batch call.** The N-bookings → N-Feign-calls anti-pattern is avoided by sending all bookingIds in one request. Empty result for a bookingId is normal (no COMPLETED invoice yet) — treat as `amount = 0`.

**Test scenario:**

1. (setup) In booking-postgres: 10 bookings in March 2026 — 6 PAID (totalPrice 100, 200, 150, 300, 250, 100), 2 CANCELLED, 2 REQUESTED. In invoice-postgres: 6 COMPLETED invoices matching the 6 PAID bookings (same amounts).
2. (action) `GET /api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31`.
3. (expect) 200 — `totalBookings=10`, `bookingsByStatus={PAID:6, CANCELLED:2, REQUESTED:2}`, `completionRate=0.6`, `totalRevenue=1100`, `averageBookingValue=183.33`.
4. (verify) Exactly one Feign call to invoice-service `POST /api/invoices/by-bookings` (not 6). No JOIN across booking-postgres and invoice-postgres.
5. (action) Empty date range → `totalBookings=0`, `totalRevenue=0`, `averageBookingValue=0`.
6. (action) `startDate` after `endDate` → 400.

---

### Features Verified as NOT Cross-Service (Booking-Specific)

**S3-F1 (Get Bookings by Status and Date Range)** — Local query on `bookings`. **No M3 change.**

**S3-F5 (Filter Bookings by Metadata Field — JSONB)** — Local JSONB query on `bookings`. **No M3 change.**

**S3-F6 (Booking Analytics by Time Period — M1 report DTO)** — Local aggregation on `bookings` (counts and `bookingsByStatus`, no revenue). The richer M2 dashboard with revenue is S3-F10 (refactored above). **S3-F6 has no M3 change.**

**S3-F8 (Add Services to Existing Booking)** — Local transactional INSERT into `booking_services` + UPDATE on `bookings.totalPrice` (intra-service). **No M3 change.**

**S3-F9 (Get Booking Details with Services)** — Local read of `bookings + booking_services` (intra-service relationship DTO). **No M3 change.**

---

### RabbitMQ: S3 Publishes

| Routing key          | Exchange         | Payload                                       | When                                       |
| -------------------- | ---------------- | --------------------------------------------- | ------------------------------------------ |
| `booking.placed`     | `booking.events` | `{bookingId, userId, providerId}`             | After S3-F2 assigns a provider             |
| `booking.completed`  | `booking.events` | `{bookingId, userId, providerId, totalPrice}` | After S3-F4 completion (saga trigger)      |
| `booking.cancelled`  | `booking.events` | `{bookingId, userId, providerId, reason}`     | After S3-F7 cancel, or after compensation  |

### RabbitMQ: S3 Consumes

All consumers below are **idempotent** — they apply state-based guards (`UPDATE bookings SET status = ? WHERE id = ? AND status IN (<allowed-prior-states>)`) so duplicate event delivery (at-least-once) does not corrupt the saga state machine.

| Routing key         | From exchange     | Action                                                                                                                                       |
| ------------------- | ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `payment.initiated` | `payment.events`  | If status = COMPLETING → mark = PAYMENT_PENDING. If already PAYMENT_PENDING/PAID, no-op (duplicate delivery).                                |
| `payment.completed` | `payment.events`  | If status = PAYMENT_PENDING → mark = PAID. If already PAID, no-op (duplicate delivery).                                                      |
| `payment.failed`    | `payment.events`  | If status = PAYMENT_PENDING → mark = PAYMENT_FAILED → publish `booking.cancelled` (compensation trigger). If already PAYMENT_FAILED/REFUNDED, no-op. |
| `payment.refunded`  | `payment.events`  | If status = PAYMENT_FAILED → mark = REFUNDED. If already REFUNDED, no-op.                                                                    |

Queue declaration: `booking.saga-feedback` with DLQ `booking.saga-feedback.dlq`.

### S3 Deliverables

- [ ] Expose `GET /api/bookings/user/{userId}/summary`, `active-count`, `completed-count`
- [ ] Expose `GET /api/bookings/provider/{providerId}/summary`, `active-count`, `completed-count`
- [ ] `feign.provider-service.url`, `feign.user-service.url`, `feign.calendar-service.url` in `application.yml`
- [ ] `ProviderServiceClient` with `getProvider`, `getProviderAvailability`
- [ ] `UserServiceClient` with `getUser`
- [ ] `CalendarServiceClient` with `getSlotForBooking`
- [ ] S3-F2 refactored to use Feign → provider-service; publishes `booking.placed`
- [ ] S3-F4 refactored: pre-saga Feign checks + publish `booking.completed` (no direct invoice insert, no direct provider status update)
- [ ] S3-F7 refactored: publish `booking.cancelled` (no direct provider status update)
- [ ] S3-F11 refactored to use Feign → user-service + provider-service
- [ ] S3-F12 refactored to use Feign → user-service + provider-service
- [ ] S3-F10 refactored to use Feign batch → invoice-service `POST /api/invoices/by-bookings` for `totalRevenue`
- [ ] New Booking status values added to enum: COMPLETING, PAYMENT_PENDING, PAID, PAYMENT_FAILED, REFUNDED
- [ ] RabbitMQ `booking.events` TopicExchange declared
- [ ] Consumers for `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 6 — Calendar Service Refactoring (S4)

### New Endpoints S4 Must Expose

| Endpoint                                                                  | Called by              | Returns                  | Description                                                                                                                                                  |
| ------------------------------------------------------------------------- | ---------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `GET /api/timeslots/provider/{providerId}/slot?date={date}&startTime={t}` | S3 (saga pre-check)    | `TimeSlotDTO`            | Returns the time slot for this provider whose `date` matches and whose `[startTime, endTime]` window covers the requested `startTime`. **404 if no covering slot exists.** New endpoint not in M1/M2. |

This endpoint is the saga's "active sub-entity exists" pre-check (see §8.3). A booking cannot be marked COMPLETED unless the provider's calendar has a corresponding time slot covering the appointment window — proving the appointment was actually scheduled in the calendar.

---

#### [S4-F3] Find Available Providers for Date

**Branch:** `feat/M3/calendar/S4-F3/<studentID>`  
**Endpoint:** `GET /api/timeslots/available?date={d}&specialty={s}`

**M1 implementation:** Native SQL JOIN `time_slots JOIN providers ON time_slots.provider_id = providers.id` on the shared database, filtered by `time_slots.available = true`, `time_slots.date = ?`, and optionally `providers.specialty = ?`. Groups by provider, counts available slots, sorts by `providers.rating DESC`, returns `(providerId, providerName, specialty, rating, availableSlots)`.

**M3 change:** calendar-service cannot JOIN the `providers` table (it lives in provider-postgres). Instead:

1. Local query on calendar-postgres: aggregate available slot counts per provider for the given date. Returns a list of `(providerId, availableSlotCount)`.
2. For each candidate `providerId`, call Feign → provider-service `GET /api/providers/{providerId}` to fetch `name`, `specialty`, `rating`, and `status`.
3. If a `specialty` query param was provided, drop candidates whose `specialty` does not match.
4. Build `List<AvailableProviderDTO>` with `providerId, providerName, specialty, rating, availableSlots`, sorted descending by rating.

> **Optimization:** If the candidate set is large, expose a batch endpoint `POST /api/providers/batch` on provider-service that accepts a list of providerIds and returns each provider in one round-trip. This is a recommendation, not a requirement.

**Test scenario:**

1. (setup) 3 providers in provider-postgres: Provider A (specialty="Dentist", rating=4.8), Provider B (specialty="Dentist", rating=4.2), Provider C (specialty="Barber", rating=4.5). In calendar-postgres: 3 available slots for Provider A on 2026-04-15, 1 for Provider B, 2 for Provider C.
2. (action) `GET /api/timeslots/available?date=2026-04-15&specialty=Dentist`.
3. (expect) 200 — returns Provider A and Provider B with their slot counts (A first by rating). Provider C excluded because its specialty does not match.
4. (action) `GET /api/timeslots/available?date=2026-04-15` (no specialty filter) → returns all 3 providers.
5. (verify) Feign calls to provider-service for each candidate. No JOIN on provider-postgres from calendar-service.

---

#### [S4-F9] Find Idle Providers

**Branch:** `feat/M3/calendar/S4-F9/<studentID>`  
**Endpoint:** `GET /api/timeslots/idle?maxBookedSlots={n}&sinceDays={d}`

**M1 implementation:** Native SQL `JOIN time_slots WITH providers` using a subquery to count booked slots per provider in the last `sinceDays` days, filtering by `bookedSlots <= maxBookedSlots`.

**M3 change:** Replace the JOIN with Feign calls.

1. Local query on calendar-postgres: per-provider count of booked slots (where `available=false`) in the last `sinceDays` days. Return `(providerId, bookedSlotsCount, totalSlotsCount)` tuples filtered by `bookedSlotsCount ≤ maxBookedSlots`.
2. For each `providerId`, call Feign → provider-service `GET /api/providers/{providerId}` to fetch `name`, `specialty`, `rating`.
3. Build `List<IdleProviderDTO>` with `providerId, providerName, specialty, rating, bookedSlotsCount, totalSlotsCount`.

**Test scenario:**

1. (setup) 3 providers in provider-postgres (specialties: Provider A=Tutor, Provider B=Dentist, Provider C=Barber). In calendar-postgres for the last 30 days: Provider A has 1 booked slot, Provider B has 5 booked slots, Provider C has 0 booked slots.
2. (action) `GET /api/timeslots/idle?maxBookedSlots=2&sinceDays=30`.
3. (expect) 200 — returns Provider A and Provider C (booked slots ≤ 2), with names enriched via Feign. Provider B excluded.
4. (action) `GET /api/timeslots/idle?maxBookedSlots=0&sinceDays=30` → only Provider C.
5. (verify) No JOIN across calendar-postgres and provider-postgres.

---

### Features Verified as NOT Cross-Service (Booking-Specific)

**S4-F1, S4-F2, S4-F4, S4-F8 (provider-existence checks)** — In M1 these features verified the provider exists via a native SQL count on the shared `providers` table before reading/writing time slots. In M3 the existence check is **dropped from the request path**: providers are managed by provider-service, and calendar-service treats `providerId` as an opaque token (the JWT ownership rules in M2 already gate "who can update which provider's calendar"). An orphaned `providerId` in `time_slots` is acceptable at M2/M3 scope. **No M3 change to these endpoints** beyond removing the SQL existence check that previously hit the shared `providers` table.

**S4-F5 (Filter Time Slots by Metadata)** — Local JSONB query on `time_slots`. **No M3 change.**

**S4-F6 (Get Time Slots in Date Range)** — Local query on `time_slots`. **No M3 change.**

**S4-F7 (Purge Old Time Slot Data)** — Local DELETE on `time_slots`. **No M3 change.**

**S4-F10 (Get Calendar Analytics Dashboard — M2)** — Aggregates only the local `time_slots` table. **No M3 change.**

**S4-F11 (Record Provider Availability Snapshot — M2)** — In M2 it verified the provider via shared SQL; in M3 the existence check is dropped on the same rationale as S4-F1/S4-F2. The Cassandra write + MongoDB Observer log are local-service operations. The snapshot's `total_slots` / `available_slots` / `booked_slots` counts come from the local `time_slots` table.

**S4-F12 (Get Provider Availability History — M2)** — Reads Cassandra. **No M3 change.**

### RabbitMQ: S4 Publishes

| Routing key       | Exchange           | Payload                              | When                                                                                              |
| ----------------- | ------------------ | ------------------------------------ | ------------------------------------------------------------------------------------------------- |
| `slot.reserved`   | `calendar.events`  | `{slotId, providerId, bookingId}`    | Optional — emit when consuming `booking.placed` to mark a slot as booked — *(observability only — see §2.9)* |
| `slot.released`   | `calendar.events`  | `{slotId, providerId, bookingId}`    | Optional — emit when consuming `booking.cancelled` to free a slot — *(observability only — see §2.9)*        |

### RabbitMQ: S4 Consumes

| Routing key          | From exchange     | Action                                                                                                                                                            |
| -------------------- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `booking.placed`     | `booking.events`  | Mark the matching time slot for `(providerId, appointmentDate, startTime)` as `available=false` in calendar-postgres; publish `slot.reserved` (optional)         |
| `booking.completed`  | `booking.events`  | Log a TRIP_COMPLETED-style entry to `calendar_events` (MongoDB) for audit; the time slot remains `available=false` (the appointment occupied it)                  |
| `booking.cancelled`  | `booking.events`  | Free the previously reserved time slot — set `available=true` in calendar-postgres; publish `slot.released` (optional). Audit log to `calendar_events`           |

Queue declaration: `calendar.booking.saga-listener` with DLQ `calendar.booking.saga-listener.dlq`.

### S4 Deliverables

- [ ] Implement `GET /api/timeslots/provider/{providerId}/slot?date={d}&startTime={t}` — returns the matching covering slot, 404 otherwise
- [ ] `feign.provider-service.url` in calendar-service `application.yml`
- [ ] `ProviderServiceClient` Feign interface with `getProvider`
- [ ] S4-F3 refactored to use Feign → provider-service for provider name + specialty filter
- [ ] S4-F9 refactored to use Feign → provider-service for provider name + specialty enrichment
- [ ] RabbitMQ `calendar.events` TopicExchange declared (optional `slot.reserved` / `slot.released` publishes)
- [ ] Consumer for `booking.placed`, `booking.completed`, `booking.cancelled` with auto ACK + DLQ via `x-dead-letter-exchange`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 7 — Invoice Service Refactoring (S5)

### New Endpoints S5 Must Expose

| Endpoint                                                          | Called by   | Returns                                              | Description                                                                                                                                                       |
| ----------------------------------------------------------------- | ----------- | ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/invoices/user/{userId}/total?startDate={d}&endDate={d}` | S1 (S1-F6)  | `BigDecimal`                                         | Total COMPLETED invoice amount for this user in the date range. 0.0 if no invoices.                                                                               |
| `POST /api/invoices/by-bookings`                                  | S3 (S3-F10) | `Map<Long, InvoiceAmountDTO>` (keyed by bookingId)   | Body: `{"bookingIds": [Long, ...]}`. Returns the COMPLETED-invoice `amount` for each requested bookingId, omitting any bookingId without a COMPLETED invoice. Used by the analytics dashboard to avoid an N-Feign-calls pattern. |

---

#### [S5-F3] User Invoice Summary

**Branch:** `feat/M3/invoice/S5-F3/<studentID>`  
**Endpoint:** `GET /api/invoices/user/{userId}/summary`

**M1 implementation:** Native SQL `SELECT COUNT(*) FROM users WHERE id = ?` on the shared database to verify the user exists before returning invoice data.

**M3 change:** Replace the user existence check with Feign → user-service `GET /api/users/{userId}`. 404 from Feign → throw 404. Otherwise build the breakdown from the local `invoices` table as in M1.

**Test scenario:**

1. (setup) User ID=1 in user-postgres. 4 COMPLETED invoices in invoice-postgres for userId=1: 2 CREDIT_CARD (200, 350), 1 CASH (150), 1 WALLET (100).
2. (action) `GET /api/invoices/user/1/summary`.
3. (expect) 200 — `totalInvoices=4, totalAmount=800, methodBreakdown={CREDIT_CARD:550, CASH:150, WALLET:100}`.
4. (action) `GET /api/invoices/user/999/summary` → Feign → user-service throws 404 → 404.

---

#### [S5-F4] Process Invoice for Booking

**Branch:** `feat/M3/invoice/S5-F4/<studentID>`  
**Endpoint:** `POST /api/invoices/booking/{bookingId}`  
**Body:** `{"method": String, "cardLastFour": String}`  
**Auth:** `Authenticated` — caller must be the booking's owner (`X-User-Id == bookingDTO.userId`) or `ADMIN`. Reject with 403 otherwise — prevents User A from charging User B's booking.

**M1 implementation:** `SELECT * FROM bookings WHERE id = ?` on the shared database to validate the booking exists and is COMPLETED. Then updates the existing PENDING invoice row that S3-F4 created when the booking was completed.

**M3 change:** Replace the booking lookup with Feign → booking-service `GET /api/bookings/{bookingId}`. The endpoint is OUTSIDE the saga's automatic forward path — it's an explicit user action — and **must be idempotent** under concurrent retry (`SELECT … FOR UPDATE` on the PENDING Invoice is the idempotency anchor; see §16 Critical Rule 11). The Invoice status enum gains a new value `PROCESSING` for the brief in-flight window between locking the row and publishing the result event.

Behavior (transactional):

1. **Locate and lock the existing PENDING `Invoice`** for `bookingId` in invoice-postgres via `SELECT … FOR UPDATE` inside the transaction. The saga's `booking.completed` consumer in S5 created exactly one PENDING Invoice per booking — this row is the idempotency anchor.
   - If the lock target's status is already `PROCESSING` / `COMPLETED` / `FAILED` / `REFUNDED` → throw **409** ("Concurrent or duplicate payment request").
   - If no PENDING Invoice exists → throw **404** ("No pending invoice — saga has not yet run for this booking").
2. Mark the locked Invoice's status = `PROCESSING` and commit. Subsequent concurrent calls now see PROCESSING and receive 409.
3. Feign → booking-service `GET /api/bookings/{bookingId}` to confirm `status = PAYMENT_PENDING`. If the booking is not in PAYMENT_PENDING (e.g., already PAID, or saga compensation already ran), revert Invoice to `PENDING` and throw 400.
4. Attempt to process the invoice (in M2 this was a synchronous mock).
5. On success: Invoice → `COMPLETED`, populate `transactionDetails` JSONB (`gatewayResponse=approved`, `cardLastFour`, plus the `cancellationFee` key per Section 4.6 of the M2 spec). Publish `payment.completed` to `payment.events`. Return 201 with the COMPLETED Invoice.
6. On failure (or `?simulateFailure=true` per Section 7.1.6 of the M2 spec): Invoice → `FAILED`. Publish `payment.failed` to `payment.events`. Return 400.

> **Invoice status enum (M3 update):** the M2 enum (`PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`) gains one new value `PROCESSING` for the in-flight payment window. Additive only — M2 features that filter on `status = COMPLETED` continue to work; the PROCESSING state is invisible to anything outside this endpoint's transaction.

**Test scenario:**

1. (setup) Booking ID=1 in booking-postgres: status=PAYMENT_PENDING, totalPrice=350. PENDING Invoice in invoice-postgres for bookingId=1, amount=350.
2. (action) `POST /api/invoices/booking/1` body `{"method": "CREDIT_CARD", "cardLastFour": "4242"}` with the booking owner's token.
3. (expect) 201 — invoice status=COMPLETED. `payment.completed` event published. After consumption: booking status=PAID.
4. (verify) Feign call to booking-service to validate status=PAYMENT_PENDING. No direct query on booking-postgres.
5. (action) Re-issue the same `POST /api/invoices/booking/1` after step 3 (Invoice now COMPLETED) → **409** (concurrent/duplicate request — Invoice already past PENDING).
6. (action) Two simultaneous `POST /api/invoices/booking/2` requests for a fresh PAYMENT_PENDING booking with one PENDING Invoice. (expect) First wins 201 (Invoice → PROCESSING → COMPLETED, `payment.completed` published); second sees PROCESSING under the row lock and returns **409**.
7. (setup) Booking ID=3 in booking-postgres: status=PAYMENT_PENDING, but **no PENDING Invoice** in invoice-postgres (saga has not yet processed `booking.completed`). (action) `POST /api/invoices/booking/3`. (expect) **404** ("No pending invoice — saga has not yet run").
8. (action) User A's token calling `POST /api/invoices/booking/1` for User B's booking (different `userId`). (expect) **403** (ownership violation per Auth declaration above).
9. (action) Booking ID=4 with status=CONFIRMED (not PAYMENT_PENDING) but a PENDING Invoice exists. (action) `POST /api/invoices/booking/4`. (expect) Invoice locks → flips to PROCESSING → Feign confirms booking is CONFIRMED → revert Invoice to PENDING → 400.
10. (action) Body with `?simulateFailure=true` → 400 + `payment.failed` event published, Invoice → FAILED.

---

#### [S5-F10] Get Revenue by Service Type with Cancellation Fee Breakdown *(M2)*

**Branch:** `feat/M3/invoice/S5-F10/<studentID>`  
**Endpoint:** `GET /api/invoices/analytics/service-type?startDate={date}&endDate={date}`

**M2 implementation:** Three-table JOIN: `invoices JOIN bookings ON bookings.id = invoices.booking_id JOIN providers ON providers.id = bookings.provider_id` — reads `providers.specialty` from the shared database to group revenue by service type.

**M3 change:** Two Feign-call rounds replace the JOIN:

1. Fetch all in-range invoices (status COMPLETED or REFUNDED) from invoice-postgres (local query, no JOIN).
2. For each invoice, Feign → booking-service `GET /api/bookings/{bookingId}` → get `providerId` and the booking's `status` (needed for cancellation rate).
3. For each `providerId`, Feign → provider-service `GET /api/providers/{providerId}` → read `specialty`.
4. Group by `specialty`, aggregate `cancellationFeeRevenue` (sum of `transactionDetails->>'cancellationFee'`, defaulting to 0 when the key is missing per §4.6 of M2), `netBookingRevenue = sum(amount - refundAmount)` (where `refundAmount` is taken from `transactionDetails->>'refundAmount'` for REFUNDED rows, 0 for COMPLETED rows), `totalRevenue = cancellationFeeRevenue + netBookingRevenue`, `bookingCount = count(distinct bookingId)`, `cancellationRate = count(booking.status=CANCELLED) / bookingCount`.

> **Optimization:** Cache the `providerId → specialty` lookup locally (in a Map) within the request lifecycle to avoid calling provider-service once per invoice when many invoices share the same provider.

**Test scenario:**

1. (setup) Providers in provider-postgres: P1 (specialty=Dentist), P2 (specialty=Barber), P3 (specialty=Dentist). In booking-postgres: 3 COMPLETED Dentist bookings (P1/P3) totaling 600, 1 REFUNDED Dentist booking on P1 (amount=200, cancellationFee=100, refundAmount=100), 2 COMPLETED Barber bookings (P2) totaling 400, 1 CANCELLED Barber booking on P2 (no invoice). In invoice-postgres: invoices reflecting the COMPLETED + REFUNDED data.
2. (action) `GET /api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31`.
3. (expect) 200 — `[{specialty: "Dentist", cancellationFeeRevenue: 100, netBookingRevenue: 700, totalRevenue: 800, bookingCount: 4, cancellationRate: 0.0}, {specialty: "Barber", cancellationFeeRevenue: 0, netBookingRevenue: 400, totalRevenue: 400, bookingCount: 3, cancellationRate: 0.333}]`.
4. (verify) No JOIN across invoice-postgres, booking-postgres, and provider-postgres. Two rounds of Feign calls.

---

### Features Verified as NOT Cross-Service (Booking-Specific)

**S5-F1 (Get Invoices by Status and Date Range)** — Local query on `invoices`. **No M3 change.**

**S5-F2 (Process Refund)** — Local update on `invoices`. **No M3 change.**

**S5-F5 (Apply Discount to Invoice)** — Operates on `invoices`, `discounts`, `invoice_discounts` — all in invoice-service. **No M3 change.**

**S5-F6 (Revenue Report by Date Range)** — Local aggregation on `invoices`. **No M3 change.**

**S5-F7 (Retry Failed Invoice)** — Local update on `invoices`. **No M3 change.**

**S5-F8 (Invoice Details with Discounts)** — Local read of `invoices + invoice_discounts + discounts`. **No M3 change.**

**S5-F9 (Most Used Discounts Report)** — Local aggregation on `invoice_discounts + discounts`. **No M3 change.**

**S5-F11 (Payment Method Breakdown — M2)** — Reads MongoDB `payment_audit_trail`. **No M3 change.**

**S5-F12 (Process Cancellation Refund with Timing Handling — M2)** — In M2 this feature looked up the associated `Booking` via the shared database to read `appointmentDate` and `status` for strategy selection. **In M3 this becomes cross-service: replace the local SQL `bookings` lookup with a Feign call to booking-service `GET /api/bookings/{bookingId}` to obtain `appointmentDate` and `status`.** All other behavior (Strategy selection, REFUND_DENIED audit, cache invalidation, JSONB write) is unchanged. The booking's status is *not* updated by invoice-service directly — invoice-service publishes `payment.refunded`, and booking-service's `payment.refunded` consumer (declared in §5) flips the booking status to REFUNDED. If the saga path requires further compensation, booking-service emits `booking.cancelled` from its own consumer. This keeps each service's writes within its own DB.

> **S5-F12 deliverable note:** add this Feign integration to your S5 work; it is in addition to the S5-F12 Strategy implementation from M2.

### RabbitMQ: S5 Publishes

| Routing key         | Exchange         | Payload                                | When                                                              |
| ------------------- | ---------------- | -------------------------------------- | ----------------------------------------------------------------- |
| `payment.initiated` | `payment.events` | `{invoiceId, bookingId, amount}`       | After consuming `booking.completed` and creating a PENDING invoice |
| `payment.completed` | `payment.events` | `{invoiceId, bookingId, amount}`       | After S5-F4 successfully processes invoice                        |
| `payment.failed`    | `payment.events` | `{invoiceId, bookingId, reason}`       | After S5-F4 fails to process invoice                              |
| `payment.refunded`  | `payment.events` | `{invoiceId, bookingId, refundAmount}` | After consuming `booking.cancelled` and refunding the invoice     |

### RabbitMQ: S5 Consumes

| Routing key          | From exchange     | Action                                                                                                                                                                                                                                                                                            |
| -------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `booking.completed`  | `booking.events`  | **Idempotent:** before insert, check whether a PENDING/COMPLETED `Invoice` for this `bookingId` already exists (`SELECT … FOR UPDATE` or unique constraint on `bookingId`); if yes, no-op. Otherwise create a PENDING `Invoice` in invoice-postgres with `bookingId`, `userId`, `amount=totalPrice`, status=PENDING. Publish `payment.initiated`. |
| `booking.cancelled`  | `booking.events`  | **Idempotent:** if `payment.refunded` was already emitted for this booking (Invoice status is REFUNDED), no-op. Otherwise: if a PENDING/COMPLETED `Invoice` exists for this booking → run the M2 S5-F12 Strategy refund logic to compute the refund amount and cancellation fee → set status=REFUNDED → publish `payment.refunded` |

Queue declaration: `payment.saga-listener` with DLQ `payment.saga-listener.dlq`.

### S5 Deliverables

- [ ] Expose `GET /api/invoices/user/{userId}/total?startDate=&endDate=`
- [ ] Expose `POST /api/invoices/by-bookings` — batch lookup returning `Map<Long, InvoiceAmountDTO>` for COMPLETED invoices, used by S3-F10 dashboard
- [ ] `feign.user-service.url`, `feign.booking-service.url`, `feign.provider-service.url` in `application.yml`
- [ ] `UserServiceClient` Feign interface with `getUser`
- [ ] `BookingServiceClient` Feign interface with `getBooking`
- [ ] `ProviderServiceClient` Feign interface with `getProvider`
- [ ] S5-F3 refactored to use Feign → user-service for user existence check
- [ ] S5-F4 refactored to use Feign → booking-service for booking status validation; publishes `payment.completed` or `payment.failed`
- [ ] S5-F10 refactored to use Feign → booking-service + provider-service for service-type breakdown
- [ ] S5-F12 refactored to use Feign → booking-service for `appointmentDate` and `status` (Strategy selection input)
- [ ] RabbitMQ `payment.events` TopicExchange declared
- [ ] Consumer for `booking.completed`: create PENDING invoice, publish `payment.initiated`
- [ ] Consumer for `booking.cancelled`: refund if invoice exists, publish `payment.refunded`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 8 — Booking Lifecycle Saga & Cancellation Cascade

### 8.1 What Is a Choreography Saga

When a business transaction spans multiple services, there is no distributed rollback. The Choreography Saga achieves eventual consistency through:

1. **Forward path:** each service listens for the previous step's success event and executes its part.
2. **Compensation path:** on failure, the failing service publishes a failure event; every service that already committed reverses its local change on receipt of the compensation event.

For Booking, the saga binds the *appointment lifecycle* to the *invoice lifecycle*: a provider/client completes the appointment, the invoice is settled asynchronously, and a failed settlement reverses every committed side effect (provider freed, slot released, stats reversed, invoice refunded with the timing-aware Strategy from M2 S5-F12).

### 8.2 Saga Overview — All 5 Services

The saga is triggered by `PUT /api/bookings/{id}/complete` (S3-F4).

```
TRIGGER: PUT /api/bookings/{id}/complete

[S3 — Pre-saga Feign checks (all 3 must pass)]
  → Feign → user-service:     GET /api/users/{userId}                                     status must be ACTIVE
  → Feign → provider-service: GET /api/providers/{providerId}                             status must be BUSY (assigned to this booking)
  → Feign → calendar-service: GET /api/timeslots/provider/{providerId}/slot?date=...&startTime=...   covering slot must exist

[If any check fails → 400. No events published. Booking stays IN_PROGRESS.]

══════════════════════ PROVIDER MARKS BOOKING AS COMPLETE ══════════════════════

[S3] Booking → COMPLETING (saga-status, awaiting payment)
     publishes → booking.completed {bookingId, userId, providerId, totalPrice}
                    │
      ┌─────────────┼──────────────┬─────────────────┐
      ▼             ▼              ▼                  ▼
   [S1]          [S2]           [S4]              [S5]
booking.completed booking.completed booking.completed booking.completed
consumer        consumer        consumer          consumer
      │             │              │                  │
Update client   Update          Audit log          Create
booking stats   provider: set   TRIP_COMPLETED     PENDING Invoice
(local DB)      AVAILABLE       in calendar_events (local DB,
                + bump          (slot stays        amount=totalPrice)
                earnings        booked - the      publishes →
                (local DB)      appointment       payment.initiated
                                occupied it)
                                      │                  │
                                      └──────────────────┘
                                              │
                               [S3 consumes payment.initiated]
                               Booking → PAYMENT_PENDING

══════════ CLIENT TRIGGERS PAYMENT (separate call) ══════════

[S5] POST /api/invoices/booking/{bookingId}
     Feign → S3: GET /api/bookings/{bookingId}  (must be PAYMENT_PENDING)
     processes invoice (M2 mock)
     publishes → payment.completed  OR  payment.failed

[S3 consumes payment.completed] → Booking → PAID  ✅ SAGA DONE

══════════════ COMPENSATION (payment.failed) ══════════════

[S3 consumes payment.failed] → Booking → PAYMENT_FAILED
     publishes → booking.cancelled {bookingId, userId, providerId, reason: "payment_failed"}
                    │
      ┌─────────────┼──────────────┬─────────────────┐
      ▼             ▼              ▼                  ▼
   [S1]          [S2]           [S4]              [S5]
booking.cancelled booking.cancelled booking.cancelled booking.cancelled
consumer        consumer        consumer          consumer
      │             │              │                  │
Reverse client  Reverse provider Free time slot    Refund existing
stats           stats; set      (set available=    invoice via
(local DB)      provider back   true) + publish    M2 S5-F12 Strategy
                to AVAILABLE    slot.released      publishes →
                (local DB)      (local DB)         payment.refunded
                                              [S3 consumes payment.refunded]
                                              Booking → REFUNDED
```

### 8.3 S3-F4 — Complete Booking (Saga Trigger)

**Branch:** `feat/M3/booking/S3-F4/<studentID>`  
**Endpoint:** `PUT /api/bookings/{id}/complete`  
**Auth:** `ADMIN` only — the saga trigger publishes events that fan out to 4 services and creates a PENDING Invoice. In production this would be the provider's "appointment finished" confirmation; for course scope, restrict to `ADMIN` so a non-trusted CUSTOMER token cannot fire the saga. Reject with 403 if the caller's `X-User-Role` is not `ADMIN`.

**M1 implementation:** Set status = COMPLETED + `completedAt`. Calculate `totalPrice` if unset. Update the assigned provider's status back to AVAILABLE via native SQL on the shared `providers` table. Create a PENDING `Invoice` row directly in the shared `invoices` table.

**M3 change:** Remove both the direct provider update and the direct invoice insert. Run three Feign pre-checks, then publish `booking.completed`.

Behavior:

1. Find booking by ID → 404 if not found.
2. Validate status = IN_PROGRESS → 400 if not.
3. Calculate `totalPrice` if unset by summing `BookingService.price` across the booking's services (locally — no cross-service call).
4. **Pre-saga Feign checks** (all three must pass before any event is published):
   - Feign → user-service `GET /api/users/{userId}` → status must be ACTIVE; if 404 or DEACTIVATED → 400
   - Feign → provider-service `GET /api/providers/{providerId}` → status must be BUSY (proves the provider is currently assigned and active for this booking); if 404 or any other status → 400
   - Feign → calendar-service `GET /api/timeslots/provider/{providerId}/slot?date={booking.appointmentDate}&startTime={booking.startTime}` → returns 200 with the covering time slot; 404 → 400 ("no calendar slot found for this booking")
5. Mark booking status = COMPLETING, set `completedAt = now()`, save.
6. Publish `booking.completed` to `booking.events` exchange with payload `{bookingId, userId, providerId, totalPrice}`.
7. Return 200 with the updated booking.

> Booking transitions from COMPLETING → PAYMENT_PENDING asynchronously when S3 consumes back the `payment.initiated` event from invoice-service.

**Test scenario:**

1. (setup) Booking ID=1 in booking-postgres: status=IN_PROGRESS, providerId=5, userId=10, appointmentDate=2026-04-22, startTime=10:00, endTime=11:00, totalPrice=null, with 1 BookingService (price=350). Provider ID=5 in provider-postgres: status=BUSY. User ID=10 in user-postgres: status=ACTIVE. Time slot in calendar-postgres: providerId=5, date=2026-04-22, startTime=09:30, endTime=11:30, available=false.
2. (action) `PUT /api/bookings/1/complete`.
3. (expect) 200 — booking status=COMPLETING immediately after the call, completedAt set, totalPrice=350. `booking.completed` published to `booking.events`.
4. (wait) Allow the saga to settle — events are delivered asynchronously. Poll `GET /api/bookings/1` until status = `PAYMENT_PENDING` (or sleep ~1–2 seconds; production tests should poll, not sleep).
5. (verify after event processing) No direct `UPDATE providers` or `INSERT INTO invoices` from booking-service. Booking status = PAYMENT_PENDING. invoice-postgres has a PENDING `Invoice` for bookingId=1 with amount=350.
6. (action) Same booking, but provider-service returns OFFLINE → 400. No event published.
7. (action) User status=DEACTIVATED → Feign → user-service returns DEACTIVATED → 400. No event published.
8. (action) calendar-service returns 404 (no covering slot) → 400. No event published.

---

### 8.4 S3-F7 — Cancel Booking

**Branch:** `feat/M3/booking/S3-F7/<studentID>`  
**Endpoint:** `PUT /api/bookings/{id}/cancel`  
**Auth:** `Authenticated` — the caller must be the booking's owner (`booking.userId == X-User-Id` from the JWT-forwarded header) or `ADMIN`. Reject with 403 if neither. This same endpoint is also invoked programmatically by the saga compensation path — that path runs internal-to-internal (event-driven, not HTTP) and bypasses the auth check.

**M1 implementation:** Set booking status = CANCELLED. If a provider was already assigned, run `UPDATE providers SET status = 'AVAILABLE' WHERE id = ?` directly on the shared database.

**M3 change:** Remove the direct `providers` write. Publish `booking.cancelled` — provider-service consumes it and flips its own provider row to AVAILABLE; calendar-service consumes it and frees the reserved slot; invoice-service consumes it and refunds any pre-existing invoice.

Behavior:

1. Find booking by ID → 404 if not found.
2. Validate status IN (REQUESTED, CONFIRMED) → 400 if not.
3. Set booking status = CANCELLED.
4. Publish `booking.cancelled` to `booking.events` exchange with payload `{bookingId, userId, providerId, reason: "user_requested"}`. If `providerId` is null (cancelled before assignment), the payload still carries `providerId=null` — provider-service silently ignores when it sees null.
5. Return 200.

> Provider re-availability, slot release, and any invoice refund happen asynchronously — provider-service, calendar-service, and invoice-service each consume `booking.cancelled` and update their own databases.

**Test scenario:**

1. (setup) Booking ID=1 in booking-postgres: status=CONFIRMED, providerId=5, userId=10, appointmentDate=2026-04-22, startTime=10:00. Provider ID=5 in provider-postgres: status=BUSY. Time slot in calendar-postgres: matching the booking, available=false.
2. (action) `PUT /api/bookings/1/cancel`.
3. (expect) 200 — booking status=CANCELLED. `booking.cancelled` published.
4. (verify) No direct `UPDATE providers` from booking-service. After event processing: provider-postgres has provider 5 status=AVAILABLE; calendar-postgres has the matching slot back to available=true.
5. (action) Try cancelling a COMPLETED booking → 400.
6. (action) Try cancelling an IN_PROGRESS booking → 400.
7. (action) `PUT /api/bookings/999/cancel` → 404 (booking not found). No event published.

---

### 8.5 Saga Participant Summary

| Service                  | Feign calls in saga                                                                                  | Publishes                                                                          | Consumes                                                                                |
| ------------------------ | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| **user-service**         | Target of S3 + S5 pre-checks                                                                         | `user.registered`, `user.deactivated`                                              | `booking.completed`, `booking.cancelled`                                                |
| **provider-service**     | Target of S3 pre-check + S4 enrichment                                                               | `provider.status-changed`, `provider.rated`, `provider.certification.verified`     | `booking.placed`, `booking.completed`, `booking.cancelled`                              |
| **booking-service**      | → user-service (pre-check), → provider-service (pre-check), → calendar-service (pre-check)           | `booking.placed`, `booking.completed`, `booking.cancelled`                          | `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`         |
| **calendar-service**     | Target of S3 pre-check; → provider-service (S4-F3, S4-F9 enrichment)                                 | `slot.reserved`, `slot.released` (audit only, optional)                            | `booking.placed`, `booking.completed`, `booking.cancelled`                              |
| **invoice-service**      | → user-service (S5-F3 existence), → booking-service (S5-F4, S5-F10, S5-F12), → provider-service (S5-F10) | `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`     | `booking.completed`, `booking.cancelled`                                                |

### 8.6 Saga Test Scenarios

**Scenario A — Happy path end-to-end:**

1. (setup) In respective databases: User ID=1 (ACTIVE), Provider ID=5 (BUSY, assigned to Booking ID=10), Booking ID=10 (status=IN_PROGRESS, userId=1, providerId=5, appointmentDate=2026-04-22, startTime=10:00, endTime=11:00, with 1 BookingService priced at 350), Time slot for provider 5 covering 2026-04-22 10:00–11:00 (available=false).
2. (action) `PUT /api/bookings/10/complete` → all three pre-checks pass.
3. (expect) 200. Booking status = COMPLETING immediately after the call. `booking.completed` published with `totalPrice=350`.
4. (wait) Allow the saga to settle — events are delivered asynchronously. Poll `GET /api/bookings/10` until status = `PAYMENT_PENDING` (or sleep ~1–2 seconds; production tests should poll, not sleep).
5. (verify after event processing) Booking status = PAYMENT_PENDING; provider-postgres provider 5 status = AVAILABLE with totalRatings/earnings counters incremented; invoice-postgres has a PENDING `Invoice` for bookingId=10 with amount=350.
6. (action) `POST /api/invoices/booking/10` body `{"method": "CREDIT_CARD", "cardLastFour": "4242"}` with User 1's token.
7. (expect) 201. `payment.completed` published. Booking status = PAID.

**Scenario B — Payment failure and compensation:**

1. (setup) Same as Scenario A — reach Booking status = PAYMENT_PENDING with PENDING invoice in invoice-postgres.
2. (action) `POST /api/invoices/booking/10?simulateFailure=true` (or any unsupported method) to force a gateway failure.
3. (expect) 400. `payment.failed` published. Booking → PAYMENT_FAILED.
4. (expect) `booking.cancelled` published with `reason="payment_failed"`. After event processing: client stats reversed, provider stats reversed (provider back to AVAILABLE if still BUSY), time slot released (available=true), invoice = REFUNDED via the M2 S5-F12 Strategy. Booking = REFUNDED.

**Scenario C — Pre-check failure (no covering calendar slot):**

1. (setup) User ID=1 (ACTIVE), Provider ID=5 (BUSY), Booking ID=10 (IN_PROGRESS, appointmentDate=2026-04-22, startTime=10:00). The calendar-postgres has no time slot covering provider 5 at 2026-04-22 10:00.
2. (action) `PUT /api/bookings/10/complete`.
3. (expect) 400 — calendar-service `GET /api/timeslots/provider/5/slot?date=2026-04-22&startTime=10:00` returns 404. S3 aborts before publishing any event.
4. (verify) No `booking.completed` event in RabbitMQ. Booking status still = IN_PROGRESS. Provider stays BUSY.

### 8.7 Saga Infrastructure Deliverables

- [ ] `BookingEventConfig` in booking-service: `booking.events` TopicExchange
- [ ] `ProviderEventConfig` in provider-service: `provider.events` TopicExchange
- [ ] `UserEventConfig` in user-service: `user.events` TopicExchange
- [ ] `CalendarEventConfig` in calendar-service: `calendar.events` TopicExchange (publisher optional)
- [ ] `PaymentEventConfig` in invoice-service: `payment.events` TopicExchange
- [ ] All consumer queue declarations with DLQ (one per service per exchange it listens to)
- [ ] All event payload record classes (e.g., `BookingCompletedEvent`, `PaymentFailedEvent`)
- [ ] Saga test scenarios A, B, C verified end-to-end

---

## Section 9 — Spring Cloud Gateway

### 9.1 New Maven Module

Add `api-gateway` as the 6th module in the root `pom.xml`:

```xml
<modules>
    <module>user-service</module>
    <module>provider-service</module>
    <module>booking-service</module>
    <module>calendar-service</module>
    <module>invoice-service</module>
    <module>api-gateway</module>
</modules>
```

The gateway runs on port **8080** internally, exposed as NodePort 30080 externally.

Dependencies:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

> Spring Cloud Gateway is **reactive** (Project Reactor). Do NOT add `spring-boot-starter-web` — it conflicts with webflux. The artifact is `spring-cloud-starter-gateway-server-webflux` — the legacy name `spring-cloud-starter-gateway` was deprecated in Spring Cloud 2025.1.0 (Oakwood) and is flagged for removal; always use the `-server-webflux` form with Spring Boot 4.x.

### 9.2 Routing Configuration

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8080
          predicates:
            - Path=/api/users/**,/api/auth/**
        - id: provider-service
          uri: http://provider-service:8080
          predicates:
            - Path=/api/providers/**
        - id: booking-service
          uri: http://booking-service:8080
          predicates:
            - Path=/api/bookings/**
        - id: calendar-service
          uri: http://calendar-service:8080
          predicates:
            - Path=/api/timeslots/**,/api/calendar/**
        - id: invoice-service
          uri: http://invoice-service:8080
          predicates:
            - Path=/api/invoices/**
```

### 9.3 JWT Global Filter

The M2 `JwtFilter` was a servlet `OncePerRequestFilter` using `HttpServletRequest` / `HttpServletResponse`. Spring Cloud Gateway is **reactive** (WebFlux), so the filter must be **rewritten** — never just copy the M2 class into the api-gateway module (the application will not start with mixed Servlet/WebFlux stacks). Required conversion:

- Replace `extends OncePerRequestFilter` with `implements GlobalFilter, Ordered`. Set `@Override public int getOrder() { return -1; }` so the filter runs before any route filters.
- Replace `HttpServletRequest` / `HttpServletResponse` with `ServerWebExchange` (`exchange.getRequest()`, `exchange.getResponse()`).
- Replace `filterChain.doFilter(request, response)` with `return chain.filter(exchange);` (a `Mono<Void>`).
- Read the token via `exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)`.
- Pass through `/api/auth/**` (register/login don't have a JWT yet): if the request path matches, return `chain.filter(exchange)` immediately.
- On JWT signature/expiry failure: `exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); return exchange.getResponse().setComplete();` — no downstream call.
- On success, forward the parsed claims as headers to the downstream service:

```java
ServerHttpRequest mutated = exchange.getRequest().mutate()
    .header("X-User-Id", uid)
    .header("X-User-Role", role)
    .header("X-Correlation-ID", correlationId)
    .build();
return chain.filter(exchange.mutate().request(mutated).build());
```

The shared `JWT_SECRET` is read from the gateway's `application.yml` (same secret as M2, so M2-issued tokens remain valid across the migration). Each service still keeps its own M2 JWT filter for defense-in-depth (the gateway is the public-facing validator; services trust the forwarded `X-User-Id` / `X-User-Role` headers from the gateway).

### 9.4 Gateway Deliverables

- [ ] `api-gateway` Maven module created and added to root `pom.xml`
- [ ] `spring-cloud-starter-gateway-server-webflux` + `spring-boot-starter-webflux` dependencies
- [ ] All 5 service route entries in `application.yml`
- [ ] `JwtGatewayFilter` implemented and registered as `@Component`
- [ ] `/api/auth/**` bypass (no JWT check on register/login)
- [ ] `X-User-Id`, `X-User-Role`, `X-Correlation-ID` headers forwarded to downstream services
- [ ] `docker-compose.yml` updated: per-service postgres containers + RabbitMQ container + api-gateway service

---

## Section 10 — Kubernetes Deployment

### 10.1 Directory Structure

```
k8s/
├── namespaces/
│   └── namespace.yaml              # namespace: booking
├── secrets/
│   ├── jwt-secret.yaml
│   ├── user-postgres-secret.yaml
│   ├── provider-postgres-secret.yaml
│   ├── booking-postgres-secret.yaml
│   ├── calendar-postgres-secret.yaml
│   └── invoice-postgres-secret.yaml
├── configmaps/
│   ├── user-service-configmap.yaml
│   ├── provider-service-configmap.yaml
│   ├── booking-service-configmap.yaml
│   ├── calendar-service-configmap.yaml
│   ├── invoice-service-configmap.yaml
│   └── gateway-configmap.yaml
├── pvcs/
│   ├── user-postgres-pvc.yaml
│   ├── provider-postgres-pvc.yaml
│   ├── booking-postgres-pvc.yaml
│   ├── calendar-postgres-pvc.yaml
│   ├── invoice-postgres-pvc.yaml
│   ├── rabbitmq-pvc.yaml
│   ├── mongo-pvc.yaml
│   ├── redis-pvc.yaml
│   ├── elasticsearch-pvc.yaml
│   ├── neo4j-pvc.yaml
│   └── cassandra-pvc.yaml
├── statefulsets/
│   ├── user-postgres-statefulset.yaml
│   ├── provider-postgres-statefulset.yaml
│   ├── booking-postgres-statefulset.yaml
│   ├── calendar-postgres-statefulset.yaml
│   ├── invoice-postgres-statefulset.yaml
│   ├── rabbitmq-statefulset.yaml
│   ├── mongo-statefulset.yaml
│   ├── redis-statefulset.yaml
│   ├── elasticsearch-statefulset.yaml
│   ├── neo4j-statefulset.yaml
│   └── cassandra-statefulset.yaml
├── deployments/
│   ├── user-service-deployment.yaml
│   ├── provider-service-deployment.yaml
│   ├── booking-service-deployment.yaml
│   ├── calendar-service-deployment.yaml
│   ├── invoice-service-deployment.yaml
│   └── gateway-deployment.yaml
├── services/
│   ├── user-service-svc.yaml           # ClusterIP
│   ├── user-postgres-svc.yaml          # headless
│   ├── provider-service-svc.yaml       # ClusterIP
│   ├── provider-postgres-svc.yaml      # headless
│   ├── booking-service-svc.yaml        # ClusterIP
│   ├── booking-postgres-svc.yaml       # headless
│   ├── calendar-service-svc.yaml       # ClusterIP
│   ├── calendar-postgres-svc.yaml      # headless
│   ├── invoice-service-svc.yaml        # ClusterIP
│   ├── invoice-postgres-svc.yaml       # headless
│   ├── rabbitmq-svc.yaml
│   ├── mongo-svc.yaml
│   ├── redis-svc.yaml
│   ├── elasticsearch-svc.yaml
│   ├── neo4j-svc.yaml
│   └── cassandra-svc.yaml
└── api-gateway/
    ├── gateway-deployment.yaml
    └── gateway-service.yaml            # type: NodePort (30080)
```

### 10.2 Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: booking
```

All `kubectl` commands use `-n booking`.

### 10.3 ConfigMap Example — Booking Service

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: booking-service-configmap
  namespace: booking
data:
  SPRING_DATASOURCE_URL: jdbc:postgresql://booking-postgres:5432/bookingdb-bookings
  SPRING_DATASOURCE_USERNAME: user
  SPRING_RABBITMQ_HOST: rabbitmq
  FEIGN_USER_SERVICE_URL: http://user-service:8080
  FEIGN_PROVIDER_SERVICE_URL: http://provider-service:8080
  FEIGN_CALENDAR_SERVICE_URL: http://calendar-service:8080
  FEIGN_INVOICE_SERVICE_URL: http://invoice-service:8080
```

### 10.4 StatefulSet — Per-Service PostgreSQL (Example: booking-postgres)

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: booking-postgres
  namespace: booking
spec:
  serviceName: booking-postgres
  replicas: 1
  selector:
    matchLabels:
      app: booking-postgres
  template:
    metadata:
      labels:
        app: booking-postgres
    spec:
      containers:
        - name: postgres
          image: postgres:17
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: booking-postgres-secret
                  key: POSTGRES_USER
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: booking-postgres-secret
                  key: POSTGRES_PASSWORD
            - name: POSTGRES_DB
              valueFrom:
                secretKeyRef:
                  name: booking-postgres-secret
                  key: POSTGRES_DB
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```

### 10.5 Deployment — Spring Boot Service (Example: booking-service)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: booking-service
  namespace: booking
spec:
  replicas: 1
  selector:
    matchLabels:
      app: booking-service
  template:
    metadata:
      labels:
        app: booking-service
    spec:
      containers:
        - name: booking-service
          image: <your-registry>/booking-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: booking-service-configmap
            - secretRef:
                name: booking-postgres-secret
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: jwt-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
```

### 10.6 API Gateway NodePort Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: booking
spec:
  type: NodePort
  selector:
    app: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```

Access the platform via: `curl http://$(minikube ip):30080/api/bookings`

All other services use `type: ClusterIP`. No service other than the gateway is reachable from outside the cluster.

### 10.7 Deployment Order

```bash
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/        # all databases first
# Wait for databases ready:
kubectl wait --for=condition=ready pod -l app=booking-postgres -n booking --timeout=120s
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/deployments/         # services after databases
kubectl apply -f k8s/services/
kubectl apply -f k8s/api-gateway/
```

### K8s Deliverables

- [ ] `k8s/namespaces/namespace.yaml` — namespace `booking`
- [ ] `k8s/secrets/jwt-secret.yaml` — shared JWT secret (base64-encoded)
- [ ] 5 PostgreSQL secrets (one per service)
- [ ] 5 PostgreSQL StatefulSets with PVC templates (`postgres:17` image)
- [ ] 5 headless Services for PostgreSQL StatefulSets
- [ ] RabbitMQ StatefulSet + Service
- [ ] MongoDB, Redis, Elasticsearch, Neo4j, Cassandra StatefulSets + headless Services (carry over from M2 Docker Compose)
- [ ] 5 Spring Boot Deployments with readiness/liveness probes on `/actuator/health`
- [ ] 5 ClusterIP Services for Spring Boot services
- [ ] 6 ConfigMaps (one per service + gateway) with all env vars
- [ ] API Gateway Deployment + NodePort Service (port 30080)

---

## Section 11 — Observability

### 11.1 Loki4J Appender (All 5 Services)

Add to each service's `pom.xml`:

```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>2.0.0</version>
</dependency>
```

#### Per-Service MDC Fields

Each service populates only the MDC keys relevant to its domain. `correlationId` is shared by all five services (set from the `X-Correlation-ID` header forwarded by api-gateway, or from the RabbitMQ message header in consumers). The remaining entity-specific keys differ:

| Service              | Entity-specific MDC keys                                                  |
| -------------------- | ------------------------------------------------------------------------- |
| **user-service**     | `userId`                                                                  |
| **provider-service** | `providerId`, `bookingId`, `routingKey`                                   |
| **booking-service**  | `bookingId`, `userId`, `providerId`, `slotId`, `invoiceId`, `routingKey`  |
| **calendar-service** | `slotId`, `providerId`, `bookingId`, `routingKey`                         |
| **invoice-service**  | `invoiceId`, `bookingId`, `userId`, `routingKey`                          |

#### `logback-spring.xml`

The example below is the **booking-service** template (the busiest service — its JSON includes every entity field). Each other service uses the same XML structure but **drops the MDC fields it does not populate** from the `<message><pattern>` block.

```xml
<configuration>
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <pattern>app=booking,service=${spring.application.name},level=%level,env=k8s</pattern>
            </label>
            <message>
                <pattern>
                    {
                      "timestamp": "%d{ISO8601}",
                      "level": "%level",
                      "service": "${spring.application.name}",
                      "thread": "%thread",
                      "logger": "%logger{36}",
                      "correlationId": "%X{correlationId:-}",
                      "userId": "%X{userId:-}",
                      "providerId": "%X{providerId:-}",
                      "bookingId": "%X{bookingId:-}",
                      "slotId": "%X{slotId:-}",
                      "invoiceId": "%X{invoiceId:-}",
                      "routingKey": "%X{routingKey:-}",
                      "message": "%msg"
                    }
                </pattern>
            </message>
        </format>
    </appender>
    <root level="INFO">
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
```

#### MDC Population

- **`correlationId`** — populated by a servlet filter (`OncePerRequestFilter`) that reads the `X-Correlation-ID` header set by api-gateway and calls `MDC.put("correlationId", value)`. The filter must clear MDC in `finally`. RabbitMQ consumers must also read the `correlationId` header from the inbound `Message` and call `MDC.put` at the start of the listener method.
- **Entity IDs** (`userId`, `providerId`, `bookingId`, `slotId`, `invoiceId`) — populated manually by service-layer methods using `MDC.put("bookingId", id.toString())` immediately before performing the operation, paired with `MDC.remove(...)` in a `finally` block to prevent leaking IDs into unrelated subsequent requests.
- **`routingKey`** — set by RabbitMQ publishers and consumers to the routing key being processed (e.g., `booking.completed`, `payment.failed`). This makes the Layer 3 RabbitMQ event audit panel (§11.3) usable.

#### Required Log Points

Each service must emit logs at the following points so the LogQL panels in §11.3 have data to query. Use SLF4J: `private static final Logger log = LoggerFactory.getLogger(<Class>.class);`.

| Log point                            | Level | Suggested message format                                                                                                                                                                                                                                                  |
| ------------------------------------ | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Controller method entry              | INFO  | `"Received {} {}"` (HTTP method, path)                                                                                                                                                                                                                                    |
| Controller method exit               | INFO  | `"Returning {} for {} {}"` (status, method, path)                                                                                                                                                                                                                         |
| Feign call — before request          | INFO  | `"Calling {}.{} with args={}"` (client, method, args)                                                                                                                                                                                                                     |
| Feign call — after success           | INFO  | `"{}.{} returned successfully"` (client, method)                                                                                                                                                                                                                          |
| Feign call — exception caught        | WARN  | `"Feign call to {} failed: {}"` (service, exception message)                                                                                                                                                                                                              |
| RabbitMQ — event published           | INFO  | `"Published {} for {}={}"` (routingKey, entityName, id)                                                                                                                                                                                                                   |
| RabbitMQ — event consumed (start)    | INFO  | `"Consuming {} for {}={}"` (routingKey, entityName, id)                                                                                                                                                                                                                   |
| RabbitMQ — event processed (success) | INFO  | `"Processed {} for {}={}"` (routingKey, entityName, id)                                                                                                                                                                                                                   |
| RabbitMQ — consumer error            | ERROR | `"Failed to process {}: {}"` (routingKey, exception message) → DLQ                                                                                                                                                                                                        |
| Saga state transition (S3 only)      | INFO  | `"Booking {} transitioning {} → {}"` (bookingId, oldStatus, newStatus)                                                                                                                                                                                                    |
| DB write success                     | INFO  | `"{} {} saved with status={}"` (entityName, id, status)                                                                                                                                                                                                                   |
| Slow operation (> threshold)         | WARN  | `"Slow {} took {}ms"` (operationName, elapsedMs) — wrap operations expected to be slow under load (e.g., S5-F10 service-type analytics, S2-F12 dashboard aggregation) with a stopwatch and emit when elapsed exceeds a threshold (e.g., 1000ms). Feeds the Layer 6 LogQL panel. |

Required in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health,info"
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

### 11.2 Dashboard per Service

Each of the 5 services has its own Grafana dashboard. Each dashboard has at minimum **3 LogQL panels** and **3 PromQL panels** chosen from the lists below. Five dashboard JSON files must be submitted (one per service).

---

### 11.3 LogQL Panel Options (choose ≥ 3 per service)

A LogQL query is built up in **three layers**, and every panel below uses all three:

1. **Label** — `{app="booking", service="booking-service", level="ERROR"}` — match log streams by the labels emitted by the Loki4J appender (§11.1). This narrows down which streams the rest of the query reads from.
2. **Line** — `|= "search-text"`, `!= "exclude"`, `| json`, `| line_format "{{...}}"` — filter and parse individual log lines within the matched streams. Because messages are JSON (§11.1), `| json` exposes every field (`correlationId`, `bookingId`, `routingKey`, …) for further filtering.
3. **Aggregator** — `count_over_time(...[1m])`, `rate(...[5m])`, `sum by (service) (...)` — turn the matching lines into time-series numbers that Grafana can plot.

#### Available Panels

1. **Error rate panel** — Count of ERROR-level log lines per service per minute.  
   *Example purpose:* Spike detection — if booking-service logs 50 ERRORs in one minute, something is wrong.

2. **Correlation ID trace panel** — Filter all log lines by a specific `X-Correlation-ID` value across all services.  
   *Example purpose:* Trace a single booking completion request from api-gateway through booking-service, provider-service, calendar-service, and invoice-service.

3. **RabbitMQ event audit panel** — Lines emitted by event publishers and consumers, filtered by routing key.  
   *Example purpose:* Show how many `booking.completed` events were published vs. how many `payment.initiated` events were consumed in the last hour.

4. **Feign call outcomes panel** — Log lines for successful Feign responses vs. `FeignException` catches.  
   *Example purpose:* Detect when provider-service is degraded — booking-service Feign calls to it start throwing exceptions.

5. **Saga state transitions panel** — Log lines at each saga step filtered by bookingId.  
   *Example purpose:* Visualize the complete saga flow for booking ID=42: COMPLETED → PAYMENT_PENDING → PAID.

6. **Slow operation warnings panel** — Log lines where elapsed time exceeded a threshold.  
   *Example purpose:* Alert when S5-F10 service-type revenue aggregation takes > 5 seconds.

---

### 11.4 PromQL Panel Options (choose ≥ 3 per service)

A PromQL query is built up in **four layers**, and every panel below uses all four:

1. **Metric** — the metric name itself, e.g., `http_server_requests_seconds_count` or `jvm_memory_used_bytes`. These are exposed by each service's `/actuator/prometheus` endpoint and scraped by Prometheus every 15s.
2. **Label** — narrow the metric down with label matchers, e.g., `{service="booking-service", uri="/api/bookings", method="GET"}`. Labels come from Spring Boot's Actuator metrics and from the `job_name` set in `prometheus.yml`.
3. **Range** — append a time window in square brackets, e.g., `[5m]` or `[1h]`. This turns the instant counter into a sequence of samples over that window so the function in layer 4 has data to operate on.
4. **Function** — `rate(...)`, `increase(...)`, `histogram_quantile(0.99, ...)`, `sum by (uri) (...)`, `topk(5, ...)` — converts the range vector into the final per-second rate, percentile, top-N, or grouped aggregate that Grafana plots.

#### Available Panels

1. **HTTP request rate panel** — Requests per second per endpoint.  
   *Example purpose:* Which booking-service endpoints are under the most load during peak hours?

2. **HTTP latency percentiles panel** — P50/P95/P99 latency per endpoint.  
   *Example purpose:* P99 latency on `GET /api/bookings/provider/{id}/summary` is 4s — Feign enrichment is slow.

3. **JVM health panel** — Heap usage, GC pause duration, thread count.  
   *Example purpose:* Memory pressure before OOM — calendar-service heap at 90% after processing 10,000 events.

4. **Database connection pool panel** — HikariCP active connections vs. pool size.  
   *Example purpose:* Pool exhaustion alert — invoice-service using 10/10 connections during saga fan-out.

5. **Cache hit/miss ratio panel** — Redis cache hits vs. misses from `cache_gets_total`.  
   *Example purpose:* S2-F12 dashboard cache hit rate — verify caching is effective.

6. **RabbitMQ throughput panel** — Messages published vs. consumed per queue.  
   *Example purpose:* Consumer lag on `payment.saga-listener` — published count > consumed count by > 100.

### 11.5 Observability Stack (K8s — monitoring namespace)

The three observability tools — **Loki**, **Prometheus**, and **Grafana** — run as their own pods inside the cluster, in a dedicated namespace called `monitoring`. Keeping them separate from the `booking` namespace means observability resources (CPU, memory, restarts) are isolated from the application services, and an issue in the app does not take down the dashboards.

The data flow uses two opposite directions:

- **Logs (push):** Each Spring Boot service runs the **Loki4J appender** (§11.1), which pushes log lines as JSON over HTTP to `http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push`. Loki itself never reaches into the services — they send to it.
- **Metrics (pull):** **Prometheus** scrapes each service's `/actuator/prometheus` endpoint on a 15-second interval (configured below). "Scrape" here just means an HTTP GET — Prometheus pulls the current metric values from each service and stores them as time-series.
- **Dashboards:** **Grafana** is configured with two datasources — Loki (for LogQL panels) and Prometheus (for PromQL panels). The 5 dashboard JSON files (one per service) are committed to the repo and provisioned into Grafana via a ConfigMap mount.

| Component  | Image                     | Role in the stack                                                   |
| ---------- | ------------------------- | ------------------------------------------------------------------- |
| Loki       | `grafana/loki:2.9.4`      | Receives JSON log streams pushed by Loki4J from each service.       |
| Prometheus | `prom/prometheus:v2.51.2` | Pulls metrics from each service's `/actuator/prometheus` every 15s. |
| Grafana    | `grafana/grafana:10.4.2`  | Dashboard UI; runs the LogQL/PromQL queries from §11.3 and §11.4.   |

Cross-namespace DNS resolution makes this work: from `monitoring`, Prometheus reaches a service in `booking` using the fully qualified name `<service-name>.booking.svc.cluster.local`. From `booking`, services push logs to `loki.monitoring.svc.cluster.local`.

#### Two Namespaces Required

The cluster must contain **two namespaces**, each defined as its own YAML file under `k8s/namespaces/`:

| Namespace    | Purpose                                                                                             | YAML file                                  |
| ------------ | --------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `booking`    | All 5 application services + their PostgreSQL + RabbitMQ + NoSQL stores. Already declared in §10.2. | `k8s/namespaces/namespace.yaml`            |
| `monitoring` | Loki + Prometheus + Grafana only. Nothing application-related deploys here.                         | `k8s/namespaces/monitoring-namespace.yaml` |

```yaml
# k8s/namespaces/monitoring-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: monitoring
```

#### Required Files & Directory Structure

All observability manifests live under `k8s/monitoring/`, separate from the application K8s tree shown in §10.1:

```
k8s/
├── namespaces/
│   ├── namespace.yaml                  # booking (already in §10.1)
│   └── monitoring-namespace.yaml       # monitoring (new — see above)
└── monitoring/
    ├── loki/
    │   ├── loki-configmap.yaml         # /etc/loki/local-config.yaml content
    │   ├── loki-pvc.yaml               # storage for log chunks (≥ 5Gi)
    │   ├── loki-statefulset.yaml       # image: grafana/loki:2.9.4, port 3100
    │   └── loki-service.yaml           # ClusterIP, port 3100 → name "loki"
    ├── prometheus/
    │   ├── prometheus-configmap.yaml   # contains prometheus.yml (scrape config below)
    │   ├── prometheus-pvc.yaml         # storage for TSDB (≥ 5Gi)
    │   ├── prometheus-deployment.yaml  # image: prom/prometheus:v2.51.2, port 9090
    │   └── prometheus-service.yaml     # ClusterIP, port 9090 → name "prometheus"
    └── grafana/
        ├── grafana-datasources.yaml    # ConfigMap — Loki + Prometheus datasource provisioning
        ├── grafana-dashboards.yaml     # ConfigMap — embeds 5 dashboard JSON files
        ├── grafana-pvc.yaml            # storage for Grafana state (≥ 1Gi)
        ├── grafana-deployment.yaml     # image: grafana/grafana:10.4.2, port 3000
        └── grafana-service.yaml        # NodePort 30030 — browser access to dashboards
```

The 5 dashboard JSON files (`user-dashboard.json`, `provider-dashboard.json`, `booking-dashboard.json`, `calendar-dashboard.json`, `invoice-dashboard.json`) are committed to `k8s/monitoring/grafana/dashboards/` and embedded into the `grafana-dashboards.yaml` ConfigMap so Grafana auto-loads them on startup.

#### Required Manifests Per Component

**Loki (StatefulSet):** Mount `loki-configmap` at `/etc/loki/`, attach the PVC at `/loki` for chunk storage. Service named `loki` so the Loki4J appender URL `http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push` resolves.

**Prometheus (Deployment):** Mount `prometheus-configmap` at `/etc/prometheus/prometheus.yml`. The ConfigMap holds the scrape config below. Attach the PVC at `/prometheus` for the TSDB.

**Grafana (Deployment):** Mount `grafana-datasources` at `/etc/grafana/provisioning/datasources/` and `grafana-dashboards` at `/etc/grafana/provisioning/dashboards/`. Service is `type: NodePort` on port 30030 so the dashboards are reachable from the host at `http://$(minikube ip):30030` (default credentials `admin/admin`, change on first login).

#### Example Manifest — Prometheus Deployment

The full file at `k8s/monitoring/prometheus/prometheus-deployment.yaml`. Loki and Grafana follow the same pattern (different image, different mount paths, different ports — see "Required Manifests Per Component" above).

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: monitoring
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
        - name: prometheus
          image: prom/prometheus:v2.51.2
          args:
            - --config.file=/etc/prometheus/prometheus.yml
            - --storage.tsdb.path=/prometheus
          ports:
            - containerPort: 9090
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: storage
              mountPath: /prometheus
          readinessProbe:
            httpGet:
              path: /-/ready
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: config
          configMap:
            name: prometheus-config       # contains prometheus.yml — see scrape config below
        - name: storage
          persistentVolumeClaim:
            claimName: prometheus-pvc
```

The companion `prometheus-service.yaml` is a `ClusterIP` Service named `prometheus` exposing port 9090 — Grafana's Prometheus datasource uses `http://prometheus.monitoring.svc.cluster.local:9090` to reach it.

#### Prometheus Scrape Config

This is the file that goes inside `prometheus-configmap.yaml` under the key `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: user-service
    static_configs:
      - targets: ['user-service.booking.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: provider-service
    static_configs:
      - targets: ['provider-service.booking.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: booking-service
    static_configs:
      - targets: ['booking-service.booking.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: calendar-service
    static_configs:
      - targets: ['calendar-service.booking.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: invoice-service
    static_configs:
      - targets: ['invoice-service.booking.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
```

#### Apply Order

```bash
kubectl apply -f k8s/namespaces/monitoring-namespace.yaml
kubectl apply -f k8s/monitoring/loki/
kubectl apply -f k8s/monitoring/prometheus/
kubectl apply -f k8s/monitoring/grafana/
kubectl wait --for=condition=ready pod -l app=loki -n monitoring --timeout=120s
kubectl wait --for=condition=ready pod -l app=prometheus -n monitoring --timeout=120s
kubectl wait --for=condition=ready pod -l app=grafana -n monitoring --timeout=120s
```

Open Grafana at `http://$(minikube ip):30030` — both datasources should be green and all 5 dashboards visible under the Booking folder.

### Observability Deliverables

- [ ] `logback-spring.xml` in all 5 services with Loki4J appender (§11.1)
- [ ] `management.endpoints.web.exposure.include: prometheus,health,info` in all 5 services
- [ ] 5 Grafana dashboard JSON files (`user-dashboard.json`, `provider-dashboard.json`, `booking-dashboard.json`, `calendar-dashboard.json`, `invoice-dashboard.json`) — ≥3 LogQL + ≥3 PromQL panels each, committed under `k8s/monitoring/grafana/dashboards/`
- [ ] `k8s/namespaces/monitoring-namespace.yaml` declaring the `monitoring` namespace
- [ ] `k8s/monitoring/loki/` — ConfigMap + PVC + StatefulSet + Service
- [ ] `k8s/monitoring/prometheus/` — ConfigMap (with the 5-job scrape config) + PVC + Deployment + Service
- [ ] `k8s/monitoring/grafana/` — datasources ConfigMap + dashboards ConfigMap + PVC + Deployment + NodePort Service (30030)
- [ ] Verified end-to-end: trigger an HTTP request via the gateway → log line appears in Loki within ~5s; metric counter increments in Prometheus within ~15s; both render in the corresponding service dashboard

---

## Section 12 — Project Folder Structure

This is the canonical layout your team's repo must end up in by the end of M3. Every file path referenced elsewhere in this spec maps onto this tree.

> **Package-name placeholder convention:** every Java package path in this section uses `<teamID>` as a placeholder — e.g., `com.<teamID>.booking.user`. Replace `<teamID>` with your team's actual package prefix from M1 (the unique value derived from your team's student IDs that the M1 grader auto-detects). Do NOT use a literal theme-named prefix such as `com.bookings.*` or `com.scheduling.*` — that would (1) break M1/M2 grader package detection, (2) make every team use identical package names (defeating plagiarism detection), and (3) require you to rename every M1/M2 source file. The Maven `groupId` follows the same rule: use your existing team groupId from M1, never a literal theme name.

```
booking-m3/                                 # git repo root
├── pom.xml                                 # parent POM — 7 modules (contracts + 5 services + api-gateway)
├── README.md
├── docker-compose.yml                      # local dev compose: 5 postgres + RabbitMQ + 5 NoSQL + 5 services + gateway
│
├── contracts/                              # Day-0 kickoff module (see §13.2 Parallelism Strategy) — depended on by all 5 services
│   ├── pom.xml
│   └── src/main/java/com/<teamID>/booking/contracts/
│       ├── feign/                          # @FeignClient interfaces — agreed Day 0, never edited per slice
│       │   ├── UserServiceClient.java
│       │   ├── ProviderServiceClient.java
│       │   ├── BookingServiceClient.java
│       │   ├── CalendarServiceClient.java
│       │   └── InvoiceServiceClient.java
│       ├── dto/                            # request/response DTOs returned by Feign
│       │   ├── UserDTO.java
│       │   ├── ProviderDTO.java
│       │   ├── ProviderAvailabilityDTO.java
│       │   ├── BookingDTO.java
│       │   ├── BookingSummaryDTO.java
│       │   ├── ProviderBookingSummaryDTO.java
│       │   ├── TimeSlotDTO.java
│       │   └── ProviderUtilizationDTO.java
│       └── events/                         # RabbitMQ event payload records
│           ├── BookingPlacedEvent.java
│           ├── BookingCompletedEvent.java
│           ├── BookingCancelledEvent.java
│           ├── ProviderStatusChangedEvent.java
│           ├── ProviderRatedEvent.java
│           ├── ProviderCertificationVerifiedEvent.java
│           ├── PaymentInitiatedEvent.java
│           ├── PaymentCompletedEvent.java
│           ├── PaymentFailedEvent.java
│           ├── PaymentRefundedEvent.java
│           ├── UserRegisteredEvent.java
│           ├── UserDeactivatedEvent.java
│           ├── SlotReservedEvent.java
│           └── SlotReleasedEvent.java
│
├── user-service/                           # S1
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/<teamID>/booking/user/
│       │   │   ├── UserServiceApplication.java          # @EnableFeignClients
│       │   │   ├── controller/                          # UserController, AuthController, SavedAddressController
│       │   │   ├── service/
│       │   │   ├── repository/
│       │   │   ├── entity/                              # User, SavedAddress
│       │   │   ├── config/
│       │   │   │   ├── FeignCorrelationConfig.java      # X-Correlation-ID interceptor
│       │   │   │   ├── CorrelationIdFilter.java         # OncePerRequestFilter — sets MDC
│       │   │   │   ├── UserEventConfig.java             # user.events TopicExchange + queues + DLQ + bindings
│       │   │   │   └── SecurityConfig.java              # M2 JWT filter retained
│       │   │   └── messaging/
│       │   │       ├── publishers/                      # UserEventPublisher (publishes user.registered, user.deactivated)
│       │   │       └── consumers/                       # BookingEventConsumer (@RabbitListener for booking.completed, booking.cancelled)
│       │   └── resources/
│       │       ├── application.yml                      # datasource → bookingdb-users; feign URLs; rabbit config
│       │       └── logback-spring.xml                   # Loki4J appender, JSON pattern, MDC fields
│       └── test/
│
├── provider-service/                       # S2
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/booking/provider/
│       ├── ProviderServiceApplication.java
│       ├── controller/                                  # ProviderController, ProviderCertificationController
│       ├── service/
│       ├── repository/
│       ├── entity/                                      # Provider, ProviderCertification, ProviderSearchDocument (ES)
│       ├── config/                                      # FeignConfig, ProviderEventConfig, SecurityConfig
│       └── messaging/
│           ├── publishers/                              # publishes provider.status-changed, provider.rated, provider.certification.verified
│           └── consumers/                               # consumes booking.placed (set BUSY), booking.completed (set AVAILABLE + earnings), booking.cancelled
│
├── booking-service/                        # S3 (saga state machine lives here)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/booking/bookings/
│       ├── BookingServiceApplication.java
│       ├── controller/                                  # BookingController, BookingServiceController
│       ├── service/
│       ├── repository/
│       ├── entity/                                      # Booking (with saga statuses), BookingService
│       ├── config/                                      # FeignConfig, BookingEventConfig, SecurityConfig
│       ├── saga/                                        # saga-specific consumers + state transitions
│       │   ├── SagaTriggerService.java                  # S3-F4 complete: pre-checks + publish booking.completed
│       │   └── PaymentEventConsumer.java                # consumes payment.initiated, payment.completed, payment.failed (compensation), payment.refunded
│       └── messaging/
│           ├── publishers/                              # publishes booking.placed, booking.completed, booking.cancelled
│           └── consumers/                               # any non-saga event consumers
│
├── calendar-service/                       # S4
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/booking/calendar/
│       ├── CalendarServiceApplication.java
│       ├── controller/                                  # TimeSlotController (incl. /api/timeslots/provider/{id}/slot)
│       ├── service/
│       ├── repository/
│       ├── entity/                                      # TimeSlot, CalendarAvailabilityEvent (Cassandra)
│       ├── config/                                      # FeignConfig, CalendarEventConfig, SecurityConfig
│       └── messaging/
│           ├── publishers/                              # publishes slot.reserved, slot.released (audit, optional)
│           └── consumers/                               # consumes booking.placed, booking.completed, booking.cancelled
│
├── invoice-service/                        # S5 (invoice processing + refund logic + Strategy)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/booking/invoice/
│       ├── InvoiceServiceApplication.java
│       ├── controller/                                  # InvoiceController, DiscountController
│       ├── service/                                     # InvoiceService, RefundService (S5-F12 Strategy)
│       ├── repository/
│       ├── entity/                                      # Invoice, Discount, InvoiceDiscount
│       ├── config/                                      # FeignConfig, PaymentEventConfig, SecurityConfig
│       └── messaging/
│           ├── publishers/                              # publishes payment.initiated, payment.completed, payment.failed, payment.refunded
│           └── consumers/                               # consumes booking.completed (creates PENDING invoice), booking.cancelled (refund)
│
├── api-gateway/                            # 6th Maven module — Spring Cloud Gateway (reactive)
│   ├── pom.xml                             # spring-cloud-starter-gateway-server-webflux + spring-boot-starter-webflux
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/<teamID>/booking/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   └── filter/
│       │       └── JwtGatewayFilter.java                # GlobalFilter, Ordered = -1
│       └── resources/
│           └── application.yml                          # routing predicates per service (5 routes)
│
├── k8s/                                    # all Kubernetes manifests
│   ├── namespaces/
│   │   ├── namespace.yaml                               # booking
│   │   └── monitoring-namespace.yaml                    # monitoring
│   ├── secrets/
│   │   ├── jwt-secret.yaml
│   │   ├── user-postgres-secret.yaml
│   │   ├── provider-postgres-secret.yaml
│   │   ├── booking-postgres-secret.yaml
│   │   ├── calendar-postgres-secret.yaml
│   │   └── invoice-postgres-secret.yaml
│   ├── configmaps/
│   │   ├── user-service-configmap.yaml
│   │   ├── provider-service-configmap.yaml
│   │   ├── booking-service-configmap.yaml
│   │   ├── calendar-service-configmap.yaml
│   │   ├── invoice-service-configmap.yaml
│   │   └── gateway-configmap.yaml
│   ├── pvcs/
│   │   ├── user-postgres-pvc.yaml
│   │   ├── provider-postgres-pvc.yaml
│   │   ├── booking-postgres-pvc.yaml
│   │   ├── calendar-postgres-pvc.yaml
│   │   ├── invoice-postgres-pvc.yaml
│   │   ├── rabbitmq-pvc.yaml
│   │   ├── mongo-pvc.yaml
│   │   ├── redis-pvc.yaml
│   │   ├── elasticsearch-pvc.yaml
│   │   ├── neo4j-pvc.yaml
│   │   └── cassandra-pvc.yaml
│   ├── statefulsets/
│   │   ├── user-postgres-statefulset.yaml               # postgres:17 — NOT 18
│   │   ├── provider-postgres-statefulset.yaml
│   │   ├── booking-postgres-statefulset.yaml
│   │   ├── calendar-postgres-statefulset.yaml
│   │   ├── invoice-postgres-statefulset.yaml
│   │   ├── rabbitmq-statefulset.yaml
│   │   ├── mongo-statefulset.yaml
│   │   ├── redis-statefulset.yaml
│   │   ├── elasticsearch-statefulset.yaml
│   │   ├── neo4j-statefulset.yaml
│   │   └── cassandra-statefulset.yaml
│   ├── deployments/
│   │   ├── user-service-deployment.yaml                 # readinessProbe + livenessProbe on /actuator/health
│   │   ├── provider-service-deployment.yaml
│   │   ├── booking-service-deployment.yaml
│   │   ├── calendar-service-deployment.yaml
│   │   └── invoice-service-deployment.yaml
│   ├── services/                           # one ClusterIP + one headless per pair
│   │   ├── user-service-svc.yaml                        # ClusterIP
│   │   ├── user-postgres-svc.yaml                       # headless (clusterIP: None)
│   │   ├── provider-service-svc.yaml
│   │   ├── provider-postgres-svc.yaml
│   │   ├── booking-service-svc.yaml
│   │   ├── booking-postgres-svc.yaml
│   │   ├── calendar-service-svc.yaml
│   │   ├── calendar-postgres-svc.yaml
│   │   ├── invoice-service-svc.yaml
│   │   ├── invoice-postgres-svc.yaml
│   │   ├── rabbitmq-svc.yaml
│   │   ├── mongo-svc.yaml
│   │   ├── redis-svc.yaml
│   │   ├── elasticsearch-svc.yaml
│   │   ├── neo4j-svc.yaml
│   │   └── cassandra-svc.yaml
│   ├── api-gateway/
│   │   ├── gateway-deployment.yaml
│   │   └── gateway-service.yaml                         # NodePort 30080
│   └── monitoring/                         # everything in `monitoring` namespace
│       ├── loki/
│       │   ├── loki-configmap.yaml                      # Loki server config
│       │   ├── loki-pvc.yaml
│       │   ├── loki-statefulset.yaml                    # grafana/loki:2.9.4
│       │   └── loki-service.yaml                        # ClusterIP, port 3100, name "loki"
│       ├── prometheus/
│       │   ├── prometheus-configmap.yaml                # 5-job scrape config
│       │   ├── prometheus-pvc.yaml
│       │   ├── prometheus-deployment.yaml               # prom/prometheus:v2.51.2
│       │   └── prometheus-service.yaml                  # ClusterIP, port 9090, name "prometheus"
│       └── grafana/
│           ├── grafana-datasources.yaml                 # ConfigMap — Loki + Prometheus datasource provisioning
│           ├── grafana-dashboards.yaml                  # ConfigMap — references the 5 JSONs below
│           ├── dashboards/
│           │   ├── user-dashboard.json                  # ≥3 LogQL + ≥3 PromQL panels
│           │   ├── provider-dashboard.json
│           │   ├── booking-dashboard.json
│           │   ├── calendar-dashboard.json
│           │   └── invoice-dashboard.json
│           ├── grafana-pvc.yaml
│           ├── grafana-deployment.yaml                  # grafana/grafana:10.4.2
│           └── grafana-service.yaml                     # NodePort 30030
│
└── .github/workflows/                      # bonus — CI/CD
    └── ci.yml
```

### 12.1 How Services Reference Files From Other Modules

The `contracts/` module is the mechanism that lets all 5 services share Feign interfaces, DTOs, and event records **without duplicating any Java code**. It is a plain Maven JAR (no Spring Boot parent, no executable) that the 5 services and the gateway depend on.

#### Parent `pom.xml` — Module Aggregator

The root `pom.xml` lists every module in build order. Maven's reactor builds `contracts` first because the 5 services declare it as a `<dependency>`:

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

#### `contracts/pom.xml` — The Shared Types Module

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.<teamID>.booking</groupId>
        <artifactId>booking-m3</artifactId>
        <version>1.0.0</version>
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

The `spring-cloud-starter-openfeign` dependency is required because the `@FeignClient` annotation lives on interfaces inside this module. Event records and DTOs are plain Java records and need no extra dependencies.

#### Each Service `pom.xml` — Depends on `contracts`

Add this to `user-service/pom.xml`, `provider-service/pom.xml`, `booking-service/pom.xml`, `calendar-service/pom.xml`, and `invoice-service/pom.xml`:

```xml
<dependency>
    <groupId>com.<teamID>.booking</groupId>
    <artifactId>contracts</artifactId>
    <version>1.0.0</version>
</dependency>
```

The api-gateway does **not** depend on `contracts` (it does not call Feign clients itself; it just forwards HTTP requests).

#### How Java Code Imports Across Modules

Once a service depends on `contracts`, every type defined there is importable like any other Java package. For example, `user-service` calling booking-service via Feign:

```java
package com.<teamID>.booking.user.service;

import com.<teamID>.booking.contracts.feign.BookingServiceClient;       // from contracts module
import com.<teamID>.booking.contracts.dto.BookingSummaryDTO;            // from contracts module
import com.<teamID>.booking.user.entity.User;                            // local to user-service
import com.<teamID>.booking.user.repository.UserRepository;              // local to user-service

@Service
public class UserBookingSummaryService {
    private final UserRepository userRepository;
    private final BookingServiceClient bookingClient;            // Feign interface from contracts

    public UserBookingSummaryService(UserRepository userRepository, BookingServiceClient bookingClient) {
        this.userRepository = userRepository;
        this.bookingClient = bookingClient;
    }

    public UserBookingSummaryDTO buildSummary(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        BookingSummaryDTO summary = bookingClient.getUserBookingSummary(userId);
        return new UserBookingSummaryDTO(user, summary);
    }
}
```

Same pattern for events — invoice-service consuming `booking.completed`:

```java
package com.<teamID>.booking.invoice.messaging.consumers;

import com.<teamID>.booking.contracts.events.BookingCompletedEvent;     // from contracts
import com.<teamID>.booking.contracts.feign.UserServiceClient;          // from contracts
import com.<teamID>.booking.invoice.entity.Invoice;                      // local

@Component
public class BookingEventConsumer {
    private final UserServiceClient userClient;
    private final InvoiceService invoiceService;

    @RabbitListener(queues = "payment.saga-listener")
    public void onBookingCompleted(BookingCompletedEvent event) {
        UserDTO user = userClient.getUser(event.userId());
        invoiceService.createPendingInvoice(event.bookingId(), event.userId(), event.totalPrice());
    }
}
```

#### Build Order — Maven Reactor Handles It Automatically

Run `mvn clean install` from the repo root. Maven's reactor:

1. Detects that `user-service` (and the other 4 services) depend on `contracts:1.0.0`.
2. Builds `contracts` **first**, installs it into the local Maven repo (`~/.m2/repository/com/<teamID>/booking/contracts/1.0.0/`).
3. Builds the 5 services in any order (no inter-service dependencies — they all only depend on `contracts`).
4. Builds `api-gateway` last (or in parallel with services — it depends on neither contracts nor any service).

For local Docker dev (`docker compose up`), each service's Dockerfile copies its own JAR — the `contracts` JAR is already baked into the service JAR via Maven's shade/repackage plugin during step 3.

#### Why This Eliminates Cross-Slice Compile Blockers

When student A starts work on `S1-READ-DB` (which calls `BookingServiceClient.getUserBookingSummary(...)`), they need that interface to exist in `contracts/` so their code compiles. Day-0 kickoff (§13.2 Parallelism Strategy) ensures:

- All 5 Feign client interfaces + all DTOs + all event records are committed to `contracts/` on **Day 0**, before any of the 15 slices begin work.
- Student A's user-service compiles immediately because `BookingServiceClient` exists in the imported `contracts` JAR — even though student G (owner of `S3-READ-DB`) hasn't yet implemented the matching `GET /api/bookings/user/{userId}/summary` endpoint inside booking-service.
- Runtime testing: student A uses `@MockBean BookingServiceClient` until student G's branch merges.

---

### 12.2 Module-to-Slice Map

The 15 deliverable slices (§13.2) map onto the folder tree as follows. Use this as a per-slice checklist of which files a member touches:

| Slice         | Touches                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `S1-READ-DB`  | `user-service/src/main/java/.../entity/` + `controller/` + `service/` + `application.yml` (datasource block); `contracts/.../feign/BookingServiceClient.java` + `InvoiceServiceClient.java`; `k8s/secrets/user-postgres-secret.yaml` + `k8s/pvcs/user-postgres-pvc.yaml` + `k8s/statefulsets/user-postgres-statefulset.yaml` + `k8s/services/user-postgres-svc.yaml`; `user-service/src/main/resources/logback-spring.xml`; LogQL panels in `k8s/monitoring/grafana/dashboards/user-dashboard.json`. |
| `S1-EVENTS`   | `user-service/src/main/java/.../config/UserEventConfig.java` + `messaging/publishers/UserEventPublisher.java` + `messaging/consumers/BookingEventConsumer.java`; `contracts/.../events/UserRegisteredEvent.java` + `UserDeactivatedEvent.java`; `k8s/configmaps/user-service-configmap.yaml` + `k8s/deployments/user-service-deployment.yaml` + `k8s/services/user-service-svc.yaml`; PromQL panels in `user-dashboard.json`.                                                                          |
| `S1-INFRA`    | user-service route block in `api-gateway/src/main/resources/application.yml`; user-service scrape job in `k8s/monitoring/prometheus/prometheus-configmap.yaml`; final assembly of `user-dashboard.json`. **Shared infra:** entire `api-gateway/` Maven module (incl. `JwtGatewayFilter.java`) + `k8s/api-gateway/gateway-deployment.yaml` + `gateway-service.yaml` (NodePort 30080) + `k8s/statefulsets/mongo-statefulset.yaml` + `k8s/services/mongo-svc.yaml` + `k8s/pvcs/mongo-pvc.yaml`.        |
| `S2-READ-DB`  | provider-service equivalent of S1-READ-DB, plus implementation of `GET /api/providers/{id}/availability`.                                                                                                                                                                                                                                                                                                                                                                                              |
| `S2-EVENTS`   | provider-service equivalent of S1-EVENTS — ProviderEventConfig + publishers (status-changed, rated, certification.verified) + consumers (booking.placed, booking.completed, booking.cancelled); `contracts/.../events/ProviderStatusChangedEvent.java` + `ProviderRatedEvent.java` + `ProviderCertificationVerifiedEvent.java`.                                                                                                                                                                       |
| `S2-INFRA`    | provider-service route + scrape entry + dashboard. **Shared infra:** `k8s/namespaces/monitoring-namespace.yaml` + entire `k8s/monitoring/loki/` + `k8s/statefulsets/redis-statefulset.yaml` + redis Service + PVC.                                                                                                                                                                                                                                                                                       |
| `S3-READ-DB`  | booking-service entity (incl. saga statuses on Booking) + new endpoints `GET /api/bookings/user/{id}/{summary,active-count,completed-count}`, `GET /api/bookings/provider/{id}/{summary,active-count,completed-count}`; `contracts/.../feign/ProviderServiceClient.java` + `UserServiceClient.java` + `CalendarServiceClient.java`; booking-postgres K8s; logback + LogQL panels.                                                                                                                       |
| `S3-EVENTS`   | booking-service `saga/` package (S3-F4 complete, S3-F7 cancel, payment-event consumers) + BookingEventConfig + publishers (booking.placed/completed/cancelled); `contracts/.../events/BookingCompletedEvent.java` etc.; booking-service Deployment + Service + ConfigMap; PromQL panels.                                                                                                                                                                                                                |
| `S3-INFRA`    | booking-service route + scrape entry + dashboard. **Shared infra:** entire `k8s/monitoring/prometheus/` (Deployment + ConfigMap holding the full 5-job scrape config + PVC + Service) + `k8s/statefulsets/neo4j-statefulset.yaml` + neo4j Service + PVC.                                                                                                                                                                                                                                                |
| `S4-READ-DB`  | calendar-service entity + new endpoint `GET /api/timeslots/provider/{providerId}/slot` (saga pre-check); `contracts/.../feign/ProviderServiceClient.java` (read uses); calendar-postgres K8s; logback + LogQL panels.                                                                                                                                                                                                                                                                                  |
| `S4-EVENTS`   | calendar-service CalendarEventConfig + publishers (slot.reserved, slot.released — optional/audit) + consumers (booking.placed/completed/cancelled); `contracts/.../events/SlotReservedEvent.java` + `SlotReleasedEvent.java`; calendar-service Deployment + Service + ConfigMap; PromQL panels.                                                                                                                                                                                                          |
| `S4-INFRA`    | calendar-service route + scrape entry + dashboard. **Shared infra:** entire `k8s/monitoring/grafana/` (Deployment + datasources ConfigMap + dashboards ConfigMap embedding all 5 JSONs + PVC + NodePort 30030) + `k8s/statefulsets/cassandra-statefulset.yaml` + cassandra Service + PVC.                                                                                                                                                                                                                |
| `S5-READ-DB`  | invoice-service entity + new endpoint `GET /api/invoices/user/{userId}/total`; `contracts/.../feign/UserServiceClient.java` + `BookingServiceClient.java` + `ProviderServiceClient.java` (read uses); invoice-postgres K8s; logback + LogQL panels.                                                                                                                                                                                                                                                     |
| `S5-EVENTS`   | invoice-service PaymentEventConfig + publishers (payment.initiated/completed/failed/refunded) + consumers (booking.completed → create PENDING invoice, booking.cancelled → refund via S5-F12 Strategy); `contracts/.../events/Payment*.java`; invoice-service Deployment + Service + ConfigMap; PromQL panels.                                                                                                                                                                                          |
| `S5-INFRA`    | invoice-service route + scrape entry + dashboard. **Shared infra:** `k8s/statefulsets/rabbitmq-statefulset.yaml` + rabbitmq Service (5672 + 15672) + PVC + `k8s/statefulsets/elasticsearch-statefulset.yaml` + ES Service + PVC + saga end-to-end test scenarios A/B/C from §8.6 (JUnit integration tests).                                                                                                                                                                                              |

---

## Section 13 — Work Distribution

### 13.1 Branch Format

```
feat/M3/<scope>/<ID>/<studentID>
```

Commit format: `feat(<scope>): <description> (studentID)`

### 13.2 The 15 Deliverables

> **Rule:** Each deliverable is a **vertical slice** that touches **all parts** of M3 — Java code, Kubernetes manifests, and observability artifacts. No deliverable is purely Java, K8s, or YAML. The 15 slices are designed so every team member works in **parallel without blocking anyone else** (see "Parallelism Strategy" below the table).

The 15 deliverables are organized as **5 services × 3 vertical slices per service**:

- **Slice A — Read & DB:** DB isolation, outbound Feign clients, exposed read endpoints, Postgres K8s, ≥3 LogQL panels, Logback config.
- **Slice B — Events & Saga:** RabbitMQ topology, publishers/consumers, saga participation, Spring Boot K8s, ≥3 PromQL panels, actuator config.
- **Slice C — Cross-Cutting Infra:** that service's gateway route entry + scrape job entry + dashboard JSON aggregation, plus **one** assigned shared-infra item.

| #      | Branch ID    | Service  | Work (Java + K8s + Observability)                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| ------ | ------------ | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **1**  | `S1-READ-DB` | user     | DB isolation (datasource → bookingdb-users, any cross-service `@ManyToOne` → `Long`); `BookingServiceClient` + `InvoiceServiceClient` Feign interfaces with try-catch error handling + correlation interceptor; user-postgres K8s (StatefulSet + PVC + Secret + headless Service); `logback-spring.xml`; ≥3 LogQL panels for user-service dashboard.                                                                                                                                              |
| **2**  | `S1-EVENTS`  | user     | `user.events` TopicExchange + publishers (`user.registered`, `user.deactivated`); consumer queue `user.booking.saga-listener` + DLQ; consumers for `booking.completed`/`booking.cancelled` updating user stats; user-service K8s Deployment + ClusterIP Service + ConfigMap; actuator config; ≥3 PromQL panels for user-service dashboard; S1-F3, S1-F4, S1-F6, S1-F9 Java refactor (Feign + event publish).                                                                                       |
| **3**  | `S1-INFRA`   | user     | user-service gateway route entry; user-service scrape job entry in `prometheus.yml`; final user-service dashboard JSON file. **Shared infra owned by this slice:** `api-gateway` Maven module (6th module in root pom.xml) + `JwtGatewayFilter` (Java) with `X-User-Id`/`X-User-Role`/`X-Correlation-ID` forwarding + `/api/auth/**` bypass + gateway K8s Deployment + NodePort Service (30080) + Mongo K8s StatefulSet + Service.                                                                 |
| **4**  | `S2-READ-DB` | provider | DB isolation (datasource → bookingdb-providers); `GET /api/providers/{id}/availability` exposed; `BookingServiceClient` + `UserServiceClient` + `CalendarServiceClient` Feign interfaces with error handling; provider-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for provider-service dashboard.                                                                                                                                                  |
| **5**  | `S2-EVENTS`  | provider | `provider.events` TopicExchange + publishers (`provider.status-changed`, `provider.rated`, `provider.certification.verified`); consumer queue `provider.booking.saga-listener` + DLQ; consumers for `booking.placed`/`booking.completed`/`booking.cancelled`; provider-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S2-F3, S2-F4, S2-F7, S2-F8, S2-F12 Java refactor.                                                                                                  |
| **6**  | `S2-INFRA`   | provider | provider-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** `monitoring` namespace YAML + Loki K8s (StatefulSet + ConfigMap + PVC + Service named `loki`) + Redis K8s StatefulSet + Service.                                                                                                                                                                                                                                                  |
| **7**  | `S3-READ-DB` | booking  | DB isolation (datasource → bookingdb-bookings); add saga statuses to Booking enum; expose `GET /api/bookings/user/{userId}/summary`, `active-count`, `completed-count`, `GET /api/bookings/provider/{providerId}/summary`, `active-count`, `completed-count`; `ProviderServiceClient` + `UserServiceClient` + `CalendarServiceClient` Feign interfaces with error handling; booking-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for booking-service dashboard. |
| **8**  | `S3-EVENTS`  | booking  | `booking.events` TopicExchange + publishers (`booking.placed`, `booking.completed`, `booking.cancelled`); consumer queue `booking.saga-feedback` + DLQ; consumers for `payment.initiated`/`payment.completed`/`payment.failed` (compensation trigger)/`payment.refunded`; booking-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S3-F2, S3-F4 (saga trigger), S3-F7 (cancel), S3-F11, S3-F12 Java refactor.                                                              |
| **9**  | `S3-INFRA`   | booking  | booking-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** Prometheus K8s (Deployment + ConfigMap holding the full 5-job `prometheus.yml` + PVC + Service named `prometheus`) + Neo4j K8s StatefulSet + Service.                                                                                                                                                                                                                                |
| **10** | `S4-READ-DB` | calendar | DB isolation (datasource → bookingdb-calendar); expose `GET /api/timeslots/provider/{providerId}/slot` (new saga pre-check endpoint); `ProviderServiceClient` Feign interface with error handling; calendar-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for calendar-service dashboard.                                                                                                                                                            |
| **11** | `S4-EVENTS`  | calendar | `calendar.events` TopicExchange + optional publishers (`slot.reserved`, `slot.released`); consumer queue `calendar.booking.saga-listener` + DLQ; consumers for `booking.placed` (mark slot booked)/`booking.completed` (audit log)/`booking.cancelled` (free slot); calendar-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S4-F3, S4-F9 Java refactor.                                                                                                                  |
| **12** | `S4-INFRA`   | calendar | calendar-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** Grafana K8s (Deployment + datasources ConfigMap pointing at Loki & Prometheus + dashboards ConfigMap embedding all 5 service dashboards + PVC + NodePort Service on 30030) + Cassandra K8s StatefulSet + Service.                                                                                                                                                                  |
| **13** | `S5-READ-DB` | invoice  | DB isolation (datasource → bookingdb-invoices); expose `GET /api/invoices/user/{userId}/total?startDate=&endDate=`; `UserServiceClient` + `BookingServiceClient` + `ProviderServiceClient` Feign interfaces with error handling; invoice-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for invoice-service dashboard.                                                                                                                                  |
| **14** | `S5-EVENTS`  | invoice  | `payment.events` TopicExchange + publishers (`payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`); consumer queue `payment.saga-listener` + DLQ; consumers for `booking.completed` (create PENDING invoice → publish `payment.initiated`) and `booking.cancelled` (refund via S5-F12 Strategy → publish `payment.refunded`); invoice-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S5-F3, S5-F4, S5-F10, S5-F12 Java refactor.            |
| **15** | `S5-INFRA`   | invoice  | invoice-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** RabbitMQ K8s (StatefulSet + Service exposing 5672 + 15672) + Elasticsearch K8s StatefulSet + Service + saga end-to-end test scenarios A/B/C from §8.6 implemented as JUnit integration tests.                                                                                                                                                                                       |

#### Parallelism Strategy — How All 15 Members Work Without Blocking Each Other

The 15 slices are designed so nobody waits for anyone else. The key is **contract-first development**: every cross-service interface is agreed in a kickoff meeting on Day 1, written down, and committed before any feature work starts. From that moment, each member writes against the contract — not against another member's implementation — so they can compile, test (with mocks), and deploy their slice independently.

1. **Day-0 kickoff contracts (committed by the team lead, ~2 hours):**
   
   - **Feign client interfaces** — every `@FeignClient` interface signature (e.g., `BookingServiceClient.getUserBookingSummary`) and the DTOs they return (`BookingSummaryDTO`, `ProviderBookingSummaryDTO`, etc.). Committed once to a `contracts/` Maven module that all services depend on.
   - **Event payload records** — every `record` class (`BookingCompletedEvent`, `PaymentFailedEvent`, …) is added to that same `contracts/` module. Routing keys + exchange names are fixed in §2.9 (no team debate).
   - **New endpoint paths + DTO shapes** — exact path, query params, response JSON. Already documented in each service's "New Endpoints" table (§3–§7).
   - **K8s Service names** — `loki`, `prometheus`, `rabbitmq`, `<svc>-postgres` — fixed up-front so DNS resolves correctly across slices.
   - **Shared YAML stub files** — `api-gateway/application.yml` (with route placeholders), `prometheus-configmap.yaml` (with scrape-job placeholders), `grafana-dashboards.yaml` ConfigMap (referencing 5 dashboard JSON paths). Each "INFRA" slice owns the *creation* of one stub; each service slice fills in its own block.

2. **Compile-time independence** — once the `contracts/` module is in place, slice 1's `BookingServiceClient.getUserBookingSummary(...)` call compiles even if slice 7 hasn't implemented `GET /api/bookings/user/{userId}/summary` yet. The interface is the only thing slice 1 needs to compile and unit-test.

3. **Runtime independence (mocking)** — for local dev each slice uses `@MockBean` on Feign clients and Testcontainers RabbitMQ. A slice can run, deploy, and verify in isolation without the other 14 slices being merged.

4. **Disjoint file ownership** — each slice writes to its own packages and YAML blocks. The only shared YAML files are `api-gateway/application.yml`, `prometheus.yml`, and `grafana-dashboards.yaml` — these have a stable structure agreed at kickoff so each slice edits only its assigned block. Merge conflicts are minimized to non-existent.

5. **Deploy-time independence** — when a slice's branch is ready, it merges into `main` whenever; the merge order is **not** prescribed because no slice depends on another slice being merged first. Integration verification (saga end-to-end, gateway routing) happens after all 15 are merged, owned by `S5-INFRA`.

### 13.3 Team Size Mapping

| Team size      | Mapping                                                                                                                                                                                     |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **15 members** | 1 deliverable per member, exactly. Default mapping.                                                                                                                                         |
| **14 members** | One member takes 2 deliverables. Recommended pairing: `S<i>-READ-DB` + `S<i>-INFRA` for any service whose INFRA slice is light (e.g., `S2-INFRA` if Loki is the team's most-familiar tool). |
| **13 members** | Two members each take 2 deliverables. Recommended: pair `S<i>-READ-DB` + `S<i>-INFRA` for two services whose INFRA assignments are smaller (e.g., S2 + S4).                                 |

### 13.4 Merge Order

Because the contract-first design eliminates compile-time dependencies, **merge order is unconstrained** — branches can be merged in any order, as long as the `contracts/` module exists in `main` first.

1. **Day 0:** Team lead merges the `contracts/` Maven module + the 3 stub YAML files (`api-gateway/application.yml`, `prometheus-configmap.yaml`, `grafana-dashboards.yaml`) into `main`.
2. **Day 1 onwards:** All 15 slices proceed in parallel; each merges to `main` when ready. No slice blocks another.
3. **Final integration:** Once all 15 slices are merged, `S5-INFRA` owner runs the saga end-to-end test scenarios A/B/C (§8.6) and signs off.

---

## Section 14 — Evaluation Format

### 14.1 Individual Presentation (~5 minutes per member)

Each member presents the branch they implemented and you will need to answer questions about your part of work

### 14.2 Demo Requirements

The team (like one member at least) must be able to run the full project from the cluster:

```bash
kubectl get pods -n booking                     # all pods Running
kubectl logs <your-service-pod> -n booking      # your service logs
curl http://$(minikube ip):30080/api/<endpoint> # your feature end-to-end
```

**For saga branch owners:** demonstrate the Booking Lifecycle Saga by triggering `PUT /api/bookings/{id}/complete` and showing the event ripple in provider-service, calendar-service, and invoice-service logs.

---

## Section 15 — Bonus

| Bonus                         | Description |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Full Testing Suite**        | (1) Unit tests for service business logic with `@MockBean` on all Feign clients. (2) RabbitMQ consumer integration tests with Testcontainers — publish an event, assert the consumer processes it and mutates the local DB. (3) Saga E2E test: trigger S3-F4, assert payment.initiated is received; then inject payment failure, assert compensation runs. |
| **CI/CD Pipeline**            | GitHub Actions: on push to `feat/*` → Maven build + JUnit + Docker build. On push to `main` → push images to a container registry. Submit as `.github/workflows/ci.yml`. |
| **Circuit Breaker**           | Add `spring-cloud-starter-circuitbreaker-resilience4j` to services with Feign calls. Configure fallback responses. Demonstrate: circuit opens on repeated failures, fallback activates, circuit recovers. |
| **Ingress**                   | Replace NodePort on api-gateway with an Ingress resource. `minikube addons enable ingress`, configure Ingress with path-based routing to the gateway. |
| **Horizontal Pod Autoscaler** | HPA on booking-service (highest traffic). CPU threshold ≥ 50%. Requires `metrics-server` in MiniKube. Demonstrate scale-out under simulated load. |
---

## Section 16 — Critical Rules

1. **No cross-service JDBC.** After M3, no service opens a JDBC connection to another service's database. Zero tolerance.
2. **Feign for reads. RabbitMQ for side-effects.** Use Feign when you need data to continue processing. Use RabbitMQ when triggering a state change in another service.
3. **Auto ACK with DLQ routing.** Use Spring's default `acknowledge-mode: auto` with `default-requeue-rejected: false`. Spring ACKs the message when the listener method returns normally and rejects when it throws — after retries are exhausted, rejected messages flow to the DLQ via the queue's `x-dead-letter-exchange` argument (no manual `basicAck`/`basicNack` calls).
4. **DLQ for every queue.** Every consumer queue has a dead-letter queue. Failed messages are never silently dropped.
5. **PostgreSQL 17.** Not PG18 — breaks Hibernate native query implicit cast operator resolution.
6. **StatefulSet for all databases.** Never use plain `Deployment` for a stateful database.
7. **Explicit constructor injection.** Consistent with M1/M2 — no Lombok.
8. **JWT validation at gateway.** Individual services retain their M2 JWT filter for defense-in-depth, but the gateway is the public-facing validator.
9. **No new tests added during grading.** Like M1/M2 the grader-provided test suite is the source of truth.
10. **15 deliverables, contract-first parallel work.** No slice waits for another slice's implementation; the `contracts/` module is the only Day-0 dependency.
11. **Idempotent saga consumers.** RabbitMQ delivers at-least-once; consumers may receive the same event multiple times. Every consumer that mutates state must guard against duplicates: state-based guards (`UPDATE … WHERE status IN (<allowed-prior-states>)`), unique constraints (e.g., `UNIQUE (bookingId)` on the Invoice table), or `SELECT … FOR UPDATE` checks. Duplicate event delivery must be a no-op, never a double-write.
12. **Atomic state transitions.** Saga status writes are a single conditional UPDATE — never a read-then-write across multiple statements. Concurrent retries must not race past each other.
