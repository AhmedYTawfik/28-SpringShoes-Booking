# 📘 Milestone 3 — Core Concepts Study Guide
## SpringShoes Booking & Scheduling Platform (Team 28)

**Purpose:** This guide explains every core concept you need to understand for your M3 evaluation.

---

# Table of Contents

1. [Microservices Architecture Overview](#1-microservices-architecture)
2. [Database Isolation (Per-Service DB)](#2-database-isolation)
3. [OpenFeign — Synchronous Inter-Service Communication](#3-openfeign)
4. [RabbitMQ — Asynchronous Messaging](#4-rabbitmq)
5. [The Choreography Saga Pattern](#5-saga-pattern)
6. [Spring Cloud Gateway (API Gateway)](#6-api-gateway)
7. [Kubernetes Deployment](#7-kubernetes)
8. [Observability Stack (Loki + Prometheus + Grafana)](#8-observability)
9. [Contracts Module — Contract-First Development](#9-contracts-module)
10. [Key Evaluation Q&A Cheat Sheet](#10-qa-cheat-sheet)

---

# 1. Microservices Architecture

## What is a Microservice?

A **microservice** is a small, independent application that does ONE thing well. Instead of one giant application (monolith), you split it into multiple small services that communicate over the network.

## Your Project's Services

| Service | What it does | Database |
|---------|-------------|----------|
| **user-service** | Manages users, registration, login, preferences | `bookingdb-users` |
| **provider-service** | Manages service providers (doctors, barbers, etc.) | `bookingdb-providers` |
| **booking-service** | Core booking logic, saga state machine | `bookingdb-bookings` |
| **calendar-service** | Time slots, provider availability | `bookingdb-calendar` |
| **invoice-service** | Invoices, payments, refunds | `bookingdb-invoices` |
| **api-gateway** | Single entry point, JWT validation, routing | No database |

## Why Microservices?

- **Independence:** Each service can be developed, deployed, and scaled independently
- **Technology freedom:** Each service can use different databases (polyglot persistence)
- **Fault isolation:** If invoice-service crashes, user-service still works
- **Team scalability:** 15 team members can work in parallel without blocking each other

## The Evolution: M1 → M2 → M3

```
M1: 5 services, ONE shared PostgreSQL database
    └── Services could SQL JOIN across each other's tables

M2: Added 6 databases (PostgreSQL + MongoDB + Redis + Elasticsearch + Neo4j + Cassandra)
    └── But services still shared the same PostgreSQL instance

M3: TRUE microservices — each service gets its OWN PostgreSQL
    └── No more cross-service SQL JOINs
    └── Services communicate via HTTP (Feign) and messages (RabbitMQ)
```

---

# 2. Database Isolation

## The Core Problem

In M1/M2, all services connected to ONE PostgreSQL:
```
jdbc:postgresql://postgres:5432/bookingdb  ← ALL services used this
```

This means user-service could directly query the `bookings` table, which belongs to booking-service. This violates microservice principles.

## The M3 Solution

Each service gets its **own PostgreSQL instance**:

```
user-service     → user-postgres:5432/bookingdb-users
provider-service → provider-postgres:5432/bookingdb-providers
booking-service  → booking-postgres:5432/bookingdb-bookings
calendar-service → calendar-postgres:5432/bookingdb-calendar
invoice-service  → invoice-postgres:5432/bookingdb-invoices
```

## What This Means in Practice

**BEFORE (M1/M2):** user-service wants to count a user's bookings:
```sql
-- user-service directly queries the bookings table (BAD in M3!)
SELECT COUNT(*) FROM bookings WHERE user_id = 1
```

**AFTER (M3):** user-service calls booking-service via HTTP:
```java
// user-service makes an HTTP call to booking-service
int count = bookingServiceClient.getActiveBookingCount(userId);
```

## Cross-Service Foreign Keys → Plain Long

In M1, entities had JPA relationships across services:
```java
// BAD in M3 — can't have @ManyToOne across databases
@ManyToOne
@JoinColumn(name = "provider_id")
private Provider provider;
```

In M3, cross-service references become plain IDs:
```java
// GOOD in M3 — just store the ID
private Long providerId;
```

**Intra-service relationships stay unchanged** (e.g., `Booking → BookingService` both live in booking-service).

## NoSQL Databases — Shared Instance

MongoDB, Redis, Elasticsearch, Neo4j, and Cassandra remain as **shared instances** (one each). Each service owns its own collections/indexes and never reads another service's data. Running 5 of each would crash MiniKube.

---

# 3. OpenFeign — Synchronous Inter-Service Communication

## What is OpenFeign?

OpenFeign is a **declarative HTTP client** for Spring. Instead of manually writing HTTP requests with `RestTemplate` or `WebClient`, you define a Java interface and Spring creates the HTTP client for you.

## How It Works

**Step 1:** Define an interface with HTTP annotations:
```java
@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {

    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getActiveBookingCount(@PathVariable Long userId);
}
```

**Step 2:** Inject and use it like a normal Java object:
```java
@Service
public class UserService {
    private final BookingServiceClient bookingClient;

    public UserService(BookingServiceClient bookingClient) {
        this.bookingClient = bookingClient;
    }

    public UserBookingSummaryDTO getSummary(Long userId) {
        // This looks like a method call, but it's actually an HTTP GET!
        BookingSummaryDTO summary = bookingClient.getUserBookingSummary(userId);
        return new UserBookingSummaryDTO(user, summary);
    }
}
```

**Behind the scenes**, Spring converts `bookingClient.getUserBookingSummary(1)` into:
```
GET http://booking-service:8080/api/bookings/user/1/summary
```

## When to Use Feign (vs. RabbitMQ)

| Use Feign When... | Use RabbitMQ When... |
|---|---|
| You need data **right now** to continue | You want to **trigger a side-effect** in another service |
| It's a **read** operation | It's a **write/mutation** in another service |
| The caller needs the **response** | The caller doesn't need to wait for completion |
| Example: "Get user details" | Example: "Provider status changed" |

## Error Handling — CRITICAL

Never let a Feign failure crash your service:
```java
try {
    BookingSummaryDTO summary = bookingClient.getUserBookingSummary(userId);
    return buildDTO(user, summary);
} catch (FeignException.NotFound e) {
    return buildDTO(user, BookingSummaryDTO.empty());  // Graceful fallback
} catch (FeignException e) {
    log.warn("booking-service unavailable: {}", e.getMessage());
    throw new ServiceUnavailableException("Booking service temporarily unavailable");
}
```

## Correlation ID Propagation

Every Feign call forwards the `X-Correlation-ID` header so you can trace a request across all services:
```java
@Bean
public RequestInterceptor correlationIdInterceptor() {
    return template -> {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            template.header("X-Correlation-ID", correlationId);
        }
    };
}
```

## Who Calls Who via Feign?

```
user-service     → booking-service (booking summary, active count)
                 → invoice-service (user invoice total)

provider-service → booking-service (provider earnings, active count)
                 → user-service (admin verification for certifications)
                 → calendar-service (utilization for dashboard)

booking-service  → provider-service (check availability before assignment)
                 → user-service (verify user exists for saga)
                 → calendar-service (verify time slot for saga)
                 → invoice-service (batch invoice amounts for analytics)

calendar-service → provider-service (enrich provider details)

invoice-service  → user-service (verify user exists)
                 → booking-service (validate booking for payment)
                 → provider-service (get specialty for analytics)
```

---

# 4. RabbitMQ — Asynchronous Messaging

## What is RabbitMQ?

RabbitMQ is a **message broker** — a middleman that receives messages from **producers** and delivers them to **consumers**. Think of it as a post office for your microservices.

## Core Concepts

### Exchange
The **router** — it receives messages and decides which queue(s) to send them to based on a **routing key**.

Your project uses **TopicExchange** — allows wildcard routing (e.g., `booking.*` matches `booking.placed`, `booking.completed`, etc.)

### Queue
A **mailbox** — stores messages until a consumer reads them. Each service has its own queue.

### Binding
The **rule** connecting an exchange to a queue. Says "send messages with routing key X to queue Y."

### Routing Key
A **label** on each message (e.g., `booking.completed`). The exchange uses this to route messages.

## Your Project's Exchanges & Events

```
Exchange: booking.events (owned by booking-service)
  ├── booking.placed     → provider-service, calendar-service
  ├── booking.completed  → user-service, provider-service, calendar-service, invoice-service
  └── booking.cancelled  → user-service, provider-service, calendar-service, invoice-service

Exchange: payment.events (owned by invoice-service)
  ├── payment.initiated  → booking-service
  ├── payment.completed  → booking-service
  ├── payment.failed     → booking-service
  └── payment.refunded   → booking-service

Exchange: user.events     → observability only (no consumers)
Exchange: provider.events → observability only (no consumers)
Exchange: calendar.events → observability only (no consumers)
```

## Event Payloads — Java Records

Events are simple data containers:
```java
public record BookingCompletedEvent(
    Long bookingId, Long userId, Long providerId, Double totalPrice
) {}

public record PaymentFailedEvent(
    Long invoiceId, Long bookingId, String reason
) {}
```

## How Publishing Works

```java
@Service
public class BookingEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishBookingCompleted(Long bookingId, Long userId,
                                        Long providerId, Double totalPrice) {
        BookingCompletedEvent event = new BookingCompletedEvent(
            bookingId, userId, providerId, totalPrice);
        rabbitTemplate.convertAndSend(
            "booking.events",         // exchange name
            "booking.completed",      // routing key
            event                     // the message payload
        );
    }
}
```

## How Consuming Works

```java
@RabbitListener(queues = "provider.booking.saga-listener")
public void onBookingCompleted(BookingCompletedEvent event) {
    // This method is called automatically when a booking.completed
    // message arrives in the queue
    Provider provider = providerRepo.findById(event.providerId());
    provider.setStatus("AVAILABLE");
    providerRepo.save(provider);
}
```

## Dead Letter Queue (DLQ) — What Happens When Processing Fails

Every queue has a **DLQ** — a "graveyard" for messages that couldn't be processed after 3 retries.

```
Queue: provider.booking.saga-listener
  └── DLQ: provider.booking.saga-listener.dlq
```

Configuration ensures this:
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: auto            # Spring auto-ACKs on success
        default-requeue-rejected: false    # Don't retry forever
        retry:
          enabled: true
          max-attempts: 3                  # Try 3 times, then send to DLQ
```

## Topology Declaration (Spring @Bean)

```java
@Configuration
public class BookingEventConfig {
    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    @Bean
    public Queue sagaFeedbackQueue() {
        return QueueBuilder.durable("booking.saga-feedback")
            .withArgument("x-dead-letter-exchange", "booking.saga-feedback.dlx")
            .withArgument("x-dead-letter-routing-key", "booking.saga-feedback.dlq")
            .build();
    }
}
```

---

# 5. The Choreography Saga Pattern

## What Problem Does It Solve?

When a booking is completed, FOUR services need to update:
1. **user-service** — update user stats
2. **provider-service** — set provider back to AVAILABLE
3. **calendar-service** — audit log
4. **invoice-service** — create a PENDING invoice

You can't use a database transaction across 4 different databases! The Saga pattern solves this.

## Two Types of Sagas

| Orchestration Saga | Choreography Saga (YOUR PROJECT) |
|---|---|
| Central coordinator tells each service what to do | No coordinator — services react to events |
| Like a conductor directing an orchestra | Like dancers following the music |
| Single point of failure | More resilient, no single point of failure |

## Your Booking Lifecycle Saga — Step by Step

### Happy Path (Everything Succeeds) ✅

```
Step 1: ADMIN calls PUT /api/bookings/{id}/complete
        ↓
Step 2: booking-service runs 3 pre-checks via Feign:
        → user-service: Is user ACTIVE? ✓
        → provider-service: Is provider BUSY? ✓
        → calendar-service: Does covering time slot exist? ✓
        ↓
Step 3: Booking status → COMPLETING
        Publishes: booking.completed event
        ↓
Step 4: Four services consume booking.completed SIMULTANEOUSLY:
        [user-service]     → Updates user booking stats
        [provider-service] → Sets provider to AVAILABLE, bumps earnings
        [calendar-service] → Audit log
        [invoice-service]  → Creates PENDING Invoice, publishes payment.initiated
        ↓
Step 5: booking-service consumes payment.initiated
        Booking status → PAYMENT_PENDING
        ↓
Step 6: Client calls POST /api/invoices/booking/{id} to pay
        invoice-service processes payment
        Publishes: payment.completed
        ↓
Step 7: booking-service consumes payment.completed
        Booking status → PAID  ✅ DONE!
```

### Compensation Path (Payment Fails) ❌

```
Step 6 FAILS: Payment fails (e.g., card declined)
        invoice-service publishes: payment.failed
        ↓
Step 7: booking-service consumes payment.failed
        Booking status → PAYMENT_FAILED
        Publishes: booking.cancelled (reason: "payment_failed")
        ↓
Step 8: Four services consume booking.cancelled:
        [user-service]     → REVERSES user stats
        [provider-service] → REVERSES provider stats, sets AVAILABLE
        [calendar-service] → FREES the time slot
        [invoice-service]  → REFUNDS the invoice using M2 Strategy pattern
                             Publishes: payment.refunded
        ↓
Step 9: booking-service consumes payment.refunded
        Booking status → REFUNDED  ✅ COMPENSATION DONE!
```

## Booking Status State Machine

```
REQUESTED → CONFIRMED → IN_PROGRESS → COMPLETING → PAYMENT_PENDING → PAID ✅
                                          ↓ (on failure)
                                    PAYMENT_FAILED → REFUNDED
```

## Idempotent Consumers — CRITICAL CONCEPT

RabbitMQ delivers **at-least-once** — a consumer might receive the same event TWICE. Every consumer must handle duplicates safely:

```java
// In BookingRepository — conditional UPDATE (idempotent!)
@Query("UPDATE Booking b SET b.status = 'PAYMENT_PENDING' " +
       "WHERE b.id = :id AND b.status = 'COMPLETING'")
int updateStatusToPaymentPending(@Param("id") Long id);
// Returns 0 if already past COMPLETING → safe duplicate handling!
```

This is a **state-based guard**: the UPDATE only succeeds if the booking is currently in the expected state. If a duplicate event arrives, the WHERE clause won't match, and the update is a no-op.

---

# 6. Spring Cloud Gateway (API Gateway)

## What is an API Gateway?

The API Gateway is the **single entry point** for all client requests. Instead of clients knowing about 5 different service URLs, they talk to ONE gateway URL.

```
WITHOUT Gateway:                    WITH Gateway:
Client → user-service:8081          Client → api-gateway:8080 → user-service
Client → provider-service:8082                                → provider-service
Client → booking-service:8083                                 → booking-service
Client → calendar-service:8084                                → calendar-service
Client → invoice-service:8085                                 → invoice-service
```

## How Routing Works

The gateway matches URL paths and forwards to the right service:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8080
          predicates:
            - Path=/api/users/**,/api/auth/**

        - id: booking-service
          uri: http://booking-service:8080
          predicates:
            - Path=/api/bookings/**
```

When a client calls `GET http://gateway:8080/api/bookings/1`, the gateway forwards it to `http://booking-service:8080/api/bookings/1`.

## JWT Validation at the Gateway

The gateway validates JWT tokens **before** forwarding requests. This is the "front door" security:

```java
@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() { return -1; }  // Run FIRST before any routing

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // /api/auth/** passes through (login/register don't have tokens yet)
        if (path.startsWith("/api/auth")) {
            return chain.filter(exchange);
        }

        // Extract and validate JWT
        String token = extractToken(exchange);
        Claims claims = validateJwt(token);

        // Forward user info as headers to downstream services
        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header("X-User-Id", uid)
            .header("X-User-Role", role)
            .header("X-Correlation-ID", correlationId)
            .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
```

## Why WebFlux (Reactive)?

Spring Cloud Gateway uses **WebFlux** (reactive/non-blocking), NOT Spring MVC (servlet-based). Key differences:
- Returns `Mono<Void>` instead of `void`
- Uses `ServerWebExchange` instead of `HttpServletRequest`
- Uses `GlobalFilter` instead of `OncePerRequestFilter`
- **Cannot** add `spring-boot-starter-web` (conflicts with webflux)

## Defense in Depth

Each service **still keeps** its own M2 JWT filter. The gateway is the public validator; services trust the `X-User-Id`/`X-User-Role` headers from the gateway but can verify independently.

---

# 7. Kubernetes Deployment

## What is Kubernetes?

Kubernetes (K8s) is a **container orchestration platform**. It manages running your Docker containers across a cluster, handling:
- **Deployment:** Starting/updating your services
- **Scaling:** Running multiple copies
- **Networking:** Services finding each other by name
- **Self-healing:** Restarting crashed containers

## Key Kubernetes Concepts

### Pod
The smallest deployable unit. Usually contains ONE container (e.g., one instance of booking-service).

### Deployment
Manages a set of identical Pods. Declares "I want 1 replica of booking-service running." If a pod dies, the Deployment creates a new one.

### StatefulSet
Like a Deployment but for **stateful** applications (databases). Provides stable network identity and persistent storage. Used for PostgreSQL, MongoDB, Redis, etc.

### Service (K8s Service)
A **stable network address** for a set of Pods. Types:
- **ClusterIP** (default): Only reachable inside the cluster (e.g., `booking-service:8080`)
- **NodePort**: Exposes on a specific port on the host machine (e.g., api-gateway on port 30080)
- **Headless** (`clusterIP: None`): For StatefulSets, provides direct pod DNS

### ConfigMap
Stores **non-sensitive** configuration as key-value pairs (database URLs, Feign URLs).

### Secret
Stores **sensitive** data (passwords, JWT secrets), base64-encoded.

### PersistentVolumeClaim (PVC)
Requests persistent storage so database data survives pod restarts.

### Namespace
A way to group resources. Your project uses:
- `booking` — all application services and databases
- `monitoring` — Loki, Prometheus, Grafana

## Your Cluster Architecture

```
Namespace: booking
├── Databases (StatefulSets):
│   ├── user-postgres, provider-postgres, booking-postgres
│   ├── calendar-postgres, invoice-postgres
│   ├── rabbitmq, mongo, redis, elasticsearch, neo4j, cassandra
│
├── Application Services (Deployments):
│   ├── user-service, provider-service, booking-service
│   ├── calendar-service, invoice-service
│   └── api-gateway (NodePort 30080 — external access)
│
└── Services (ClusterIP for internal, NodePort for gateway)

Namespace: monitoring
├── loki (StatefulSet — receives logs)
├── prometheus (Deployment — scrapes metrics)
└── grafana (Deployment — dashboards, NodePort 30030)
```

## How Services Find Each Other (DNS)

Inside Kubernetes, services have DNS names:
```
booking-service.booking.svc.cluster.local  → shortened to booking-service
user-postgres.booking.svc.cluster.local    → shortened to user-postgres
loki.monitoring.svc.cluster.local          → cross-namespace (full name needed)
```

## Health Probes

Every service has health checks so K8s knows if it's alive:
```yaml
readinessProbe:     # "Am I ready to receive traffic?"
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

livenessProbe:      # "Am I still alive?"
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
```

---

# 8. Observability Stack

## The Three Pillars

| Tool | What It Does | Data Flow | Protocol |
|------|-------------|-----------|----------|
| **Loki** | Stores and queries **logs** | Services **push** logs to Loki | HTTP POST (push) |
| **Prometheus** | Stores and queries **metrics** | Prometheus **pulls** metrics from services | HTTP GET (pull/scrape) |
| **Grafana** | **Visualizes** logs and metrics as dashboards | Queries Loki and Prometheus | Internal |

## Loki4J — Structured Logging

Each service has a `logback-spring.xml` that pushes JSON logs to Loki:
```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push</url>
    </http>
    <!-- JSON format with correlation ID, entity IDs, etc. -->
</appender>
```

**MDC (Mapped Diagnostic Context):** Adds contextual info to every log line:
```java
MDC.put("bookingId", "42");
MDC.put("correlationId", "abc-123");
log.info("Processing booking completion");
// Output: {"bookingId": "42", "correlationId": "abc-123", "message": "Processing..."}
```

## Prometheus — Metrics Scraping

Prometheus scrapes `/actuator/prometheus` on each service every 15 seconds:
```yaml
scrape_configs:
  - job_name: booking-service
    static_configs:
      - targets: ['booking-service.booking.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
```

Spring Boot auto-exposes metrics like:
- `http_server_requests_seconds_count` — request count per endpoint
- `jvm_memory_used_bytes` — heap memory usage
- `hikaricp_connections_active` — database connection pool usage

## Grafana Dashboards

Each service has a dashboard with ≥3 LogQL panels + ≥3 PromQL panels.

**LogQL example** (error rate):
```
count_over_time({app="booking", service="booking-service", level="ERROR"}[1m])
```

**PromQL example** (request rate):
```
rate(http_server_requests_seconds_count{service="booking-service"}[5m])
```

---

# 9. Contracts Module — Contract-First Development

## What Is It?

The `contracts/` module is a shared Maven JAR that contains:
- **Feign client interfaces** — the API contracts between services
- **DTOs** — data transfer objects for Feign responses
- **Event records** — RabbitMQ message payloads

## Why Is It Important?

Without `contracts/`, every service would duplicate these shared types. With it:

1. All 5 services depend on `contracts` in their `pom.xml`
2. Any service can import and use any Feign client or event type
3. All 15 team members can work in parallel — the contracts exist from Day 0

```java
// In user-service, importing from contracts module:
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
```

## Build Order

Maven builds `contracts` FIRST, then all services can compile against it:
```
contracts → user-service, provider-service, booking-service,
            calendar-service, invoice-service (parallel)
         → api-gateway (doesn't depend on contracts)
```

---

# 10. Key Evaluation Q&A Cheat Sheet

## Architecture Questions

**Q: Why can't services share one database in M3?**
A: Database isolation ensures true independence. Each service owns its data. If booking-service's DB schema changes, user-service is unaffected. This enables independent deployment and scaling.

**Q: What's the difference between Feign and RabbitMQ?**
A: Feign = synchronous HTTP for READS (you need data now). RabbitMQ = asynchronous messages for WRITES/SIDE-EFFECTS (trigger changes in other services, don't wait).

**Q: What is eventual consistency?**
A: After an event is published, not all services update simultaneously. There's a brief window where data is inconsistent. Eventually (milliseconds to seconds), all services process the event and reach a consistent state.

## Saga Questions

**Q: Why use choreography instead of orchestration?**
A: No central coordinator = no single point of failure. Each service reacts independently to events. Better resilience and team parallelism.

**Q: What happens if payment fails?**
A: Compensation path: booking-service publishes `booking.cancelled`, which triggers all 4 consumers to reverse their changes (stats reversed, provider freed, slot released, invoice refunded).

**Q: What makes consumers idempotent?**
A: State-based guards in the SQL UPDATE: `WHERE status = 'COMPLETING'`. If the same event arrives twice, the second time the WHERE clause won't match (status already changed), so it's a safe no-op.

## Gateway Questions

**Q: Why use an API Gateway?**
A: Single entry point, centralized JWT validation, path-based routing, header forwarding (X-User-Id, X-User-Role, X-Correlation-ID). Clients only know one URL.

**Q: Why WebFlux instead of Spring MVC?**
A: Spring Cloud Gateway is reactive by design. WebFlux handles many concurrent connections without blocking threads — ideal for a gateway that just proxies requests.

## Kubernetes Questions

**Q: Why StatefulSet for databases and Deployment for services?**
A: Databases need stable network identity and persistent storage (PVCs). Application services are stateless — any pod can handle any request.

**Q: How do services find each other in K8s?**
A: Kubernetes DNS. A Service named `booking-service` in namespace `booking` is reachable at `booking-service.booking.svc.cluster.local` (or just `booking-service` within the same namespace).

**Q: What are readiness and liveness probes?**
A: Readiness = "Am I ready to receive traffic?" (used during startup). Liveness = "Am I still working?" (K8s restarts the pod if it fails). Both hit `/actuator/health`.

## Observability Questions

**Q: How do you trace a request across multiple services?**
A: Correlation ID! The gateway generates a `X-Correlation-ID` UUID, forwards it to the downstream service, which passes it along via Feign and RabbitMQ. Query Loki with this ID to see all log lines from all services for that request.

**Q: What's the difference between Loki and Prometheus?**
A: Loki = logs (text events, pushed by services). Prometheus = metrics (numbers/counters, pulled/scraped by Prometheus). Grafana visualizes both.

---

*Good luck with your evaluation! 🚀*
