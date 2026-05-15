# M3 Calendar Service Implementation Plan

> **Owner:** Ahmed Yasser Tawfik (55-8947) + Ahmed Hussien (55-24913)  
> **Slices:** S4-READ-DB, S4-EVENTS, S4-INFRA  
> **Package:** `com.team28.booking.calendar`

---

## 1. Slice Overview

| Slice | Owner | Branch | Scope |
|-------|-------|--------|-------|
| S4-READ-DB | One member | `feat/M3/calendar/S4-READ-DB/55-8947` | DB isolation, new slot endpoint, Feign to provider-service, calendar-postgres K8s, logback, 3 LogQL panels |
| S4-EVENTS | Other member | `feat/M3/calendar/S4-EVENTS/55-24913` | RabbitMQ topology, consumers for booking.placed/completed/cancelled, optional publishers slot.reserved/released, S4-F3 + S4-F9 refactor, K8s Deployment/Service/ConfigMap, 3 PromQL panels |
| S4-INFRA | Combined | `feat/M3/calendar/S4-INFRA/<studentID>` | Gateway route, Prometheus scrape job, dashboard JSON, Grafana K8s, Cassandra K8s |

---

## 2. S4-READ-DB — Database Isolation + Feign + New Endpoint

### 2.1 Git Workflow

```
Branch: feat/M3/calendar/S4-READ-DB/55-8947

Commits (in order):
1. feat(calendar): isolate datasource to calendar-postgres/bookingdb-calendar (55-8947)
2. feat(calendar): add OpenFeign + contracts dependency to pom.xml (55-8947)
3. feat(calendar): add @EnableFeignClients and FeignCorrelationConfig (55-8947)
4. feat(calendar): add feign.provider-service.url to application.yml (55-8947)
5. feat(calendar): implement GET /api/timeslots/provider/{id}/slot endpoint (55-8947)
6. feat(calendar): add CorrelationIdFilter for MDC population (55-8947)
7. feat(calendar): add logback-spring.xml with Loki4J appender (55-8947)
8. feat(calendar): add calendar-postgres K8s manifests (55-8947)
9. feat(calendar): add 3 LogQL panels for calendar dashboard (55-8947)
```

### 2.2 DB Isolation

**Change in `application.yml`:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:calendar-postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:bookingdb-calendar}
    username: ${POSTGRES_USER:user}
    password: ${POSTGRES_PASSWORD:password}
```

**Verify:** `TimeSlot.providerId` is already `private Long providerId` (confirmed — no `@ManyToOne`).

**Remove:** Any native SQL that references tables from other services (e.g. `providers`). In M1, S4-F1/F2/F4/F8 had provider-existence checks via shared SQL — these are **dropped** in M3 per the spec (providerId is treated as opaque; JWT gates ownership).

### 2.3 New Endpoint: Slot Lookup (Saga Pre-Check)

**Endpoint:** `GET /api/timeslots/provider/{providerId}/slot?date={d}&startTime={t}`  
**Called by:** booking-service S3-F4 (saga pre-check before completing a booking)  
**Returns:** `TimeSlotDTO` — the slot whose `[startTime, endTime]` window covers the requested time  
**404** if no covering slot exists

**Controller:**

```java
@GetMapping("/provider/{providerId}/slot")
public ResponseEntity<TimeSlotDTO> getSlotForBooking(
        @PathVariable Long providerId,
        @RequestParam String date,
        @RequestParam String startTime) {
    LocalDate d = LocalDate.parse(date);
    LocalTime t = LocalTime.parse(startTime);
    TimeSlot slot = timeSlotService.findCoveringSlot(providerId, d, t);
    if (slot == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(toDTO(slot));
}
```

**Repository query:**

```java
@Query("SELECT t FROM TimeSlot t WHERE t.providerId = :providerId " +
       "AND t.date = :date AND t.startTime <= :startTime AND t.endTime >= :startTime")
Optional<TimeSlot> findCoveringSlot(@Param("providerId") Long providerId,
                                     @Param("date") LocalDate date,
                                     @Param("startTime") LocalTime startTime);
```

### 2.4 Feign Setup

**Dependencies** (add to `calendar-service/pom.xml`):

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>com.team28.booking</groupId>
    <artifactId>contracts</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Application class:**

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class CalendarServiceApplication { }
```

**application.yml addition:**

```yaml
feign:
  provider-service:
    url: ${FEIGN_PROVIDER_SERVICE_URL:http://provider-service:8080}
  booking-service:
    url: ${FEIGN_BOOKING_SERVICE_URL:http://booking-service:8080}
```

> **Why booking-service?** The spec (§5 line 753) explicitly lists **S4** as a caller of `GET /api/bookings/{bookingId}`. Calendar-service needs this in the `booking.placed` consumer to fetch `appointmentDate` and `startTime` (which are NOT in the event payload). It also uses it for `booking.cancelled` if `bookingId` is not stored locally.

**FeignCorrelationConfig** — forwards `X-Correlation-ID` from MDC on all outgoing Feign calls (see Sections 1-2 plan §4.2).

### 2.5 Logback + Loki4J

Create `src/main/resources/logback-spring.xml` with Loki4J appender. Calendar-service MDC fields: `correlationId`, `slotId`, `providerId`, `bookingId`, `routingKey`.

Add dependency to `pom.xml`:

```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2.6 K8s Manifests for calendar-postgres

Files to create:
- `k8s/secrets/calendar-postgres-secret.yaml`
- `k8s/pvcs/calendar-postgres-pvc.yaml`
- `k8s/statefulsets/calendar-postgres-statefulset.yaml` (image: `postgres:17`)
- `k8s/services/calendar-postgres-svc.yaml` (headless)

### 2.7 LogQL Panels (≥3)

Choose from: Error rate, Correlation ID trace, Feign call outcomes. Add to `calendar-dashboard.json`.

---

## 3. S4-EVENTS — RabbitMQ + Feature Refactoring

### 3.1 Git Workflow

```
Branch: feat/M3/calendar/S4-EVENTS/55-24913

Commits:
1. feat(calendar): add spring-boot-starter-amqp dependency (55-24913)
2. feat(calendar): add RabbitMQ connection config to application.yml (55-24913)
3. feat(calendar): add Jackson2JsonMessageConverter bean (55-24913)
4. feat(calendar): create CalendarEventConfig with exchange + queue + DLQ + bindings (55-24913)
5. feat(calendar): implement BookingEventConsumer for placed/completed/cancelled (55-24913)
6. feat(calendar): implement optional SlotEventPublisher for slot.reserved/released (55-24913)
7. feat(calendar): refactor S4-F3 to use Feign for provider enrichment (55-24913)
8. feat(calendar): refactor S4-F9 to use Feign for provider enrichment (55-24913)
9. feat(calendar): add calendar-service K8s Deployment + Service + ConfigMap (55-24913)
10. feat(calendar): add actuator config and 3 PromQL panels (55-24913)
```

### 3.2 RabbitMQ Config

**application.yml addition:**

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

**Jackson2JsonMessageConverter bean** — mandatory for `record` deserialization:

```java
@Bean
public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

### 3.3 CalendarEventConfig

See Sections 1-2 plan §4.3 for the full topology. Summary:

| Bean | Type | Name |
|------|------|------|
| Producer exchange | `TopicExchange` | `calendar.events` |
| Consumer exchange ref | `TopicExchange` | `booking.events` |
| Consumer queue | `Queue` | `calendar.booking.saga-listener` (with DLQ args) |
| DLX | `TopicExchange` | `calendar.dlx` |
| DLQ | `Queue` | `calendar.booking.saga-listener.dlq` |
| Bindings | 3× `Binding` | `booking.placed`, `booking.completed`, `booking.cancelled` |

### 3.4 Event Consumers

```java
@Component
public class BookingEventConsumer {
    private final TimeSlotService timeSlotService;
    private final CalendarEventRepository mongoRepo; // for audit
    private final RabbitTemplate rabbitTemplate; // optional publish

    @RabbitListener(queues = "calendar.booking.saga-listener")
    public void handleBookingEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        MDC.put("routingKey", routingKey);
        try {
            switch (routingKey) {
                case "booking.placed" -> handlePlaced(deserialize(message, BookingPlacedEvent.class));
                case "booking.completed" -> handleCompleted(deserialize(message, BookingCompletedEvent.class));
                case "booking.cancelled" -> handleCancelled(deserialize(message, BookingCancelledEvent.class));
            }
        } finally {
            MDC.remove("routingKey");
        }
    }
}
```

**booking.placed** → Feign-call booking-service for details, then mark slot:

```java
private void handlePlaced(BookingPlacedEvent event) {
    // 1. Feign → booking-service GET /api/bookings/{bookingId}
    //    to get appointmentDate + startTime (NOT in the event payload per §2.8)
    BookingDTO booking = bookingServiceClient.getBooking(event.bookingId());

    // 2. Find covering slot
    //    UPDATE time_slots SET available=false, booking_id=?
    //    WHERE provider_id=? AND date=? AND start_time<=? AND end_time>=? AND available=true
    //    Idempotency: the WHERE available=true guard prevents double-write

    // 3. Optional: publish slot.reserved to calendar.events
}
```

**booking.completed** → Audit log only (slot stays booked):

```java
private void handleCompleted(BookingCompletedEvent event) {
    // Log TRIP_COMPLETED to calendar_events (MongoDB)
    // Slot remains available=false (the appointment occupied it)
}
```

**booking.cancelled** → Free the slot using stored `bookingId`:

```java
private void handleCancelled(BookingCancelledEvent event) {
    // UPDATE time_slots SET available=true, booking_id=NULL
    //   WHERE booking_id=? AND available=false
    // Idempotency: WHERE available=false guard
    // No Feign call needed — bookingId was stored during booking.placed
    // Audit log to calendar_events (MongoDB)
    // Optional: publish slot.released to calendar.events
}
```

### 3.5 Consumer Idempotency

**Critical Rule 11:** All consumers must handle duplicate delivery.

| Event | Idempotency Guard |
|-------|-------------------|
| `booking.placed` | `UPDATE ... SET available=false, booking_id=? WHERE ... AND available=true` — if already false, 0 rows updated (no-op). Feign call to booking-service is safe to repeat (GET is idempotent) |
| `booking.completed` | MongoDB insert with bookingId check — if doc exists, skip |
| `booking.cancelled` | `UPDATE ... SET available=true, booking_id=NULL WHERE booking_id=? AND available=false` — if already true, 0 rows updated |

### 3.6 S4-F3 Refactoring — Find Available Providers

**Current M1:** `JOIN time_slots WITH providers` on shared DB.

**M3 Change:**

1. Local query: aggregate available slot counts per provider for date → `List<(providerId, slotCount)>`
2. For each providerId, Feign → `providerServiceClient.getProvider(providerId)` → get name, specialty, rating, status
3. If `specialty` param provided, filter by it
4. Build `List<AvailableProviderDTO>` sorted by rating DESC

```java
public List<AvailableProviderDTO> findAvailableProviders(LocalDate date, String specialty) {
    List<Object[]> localResults = timeSlotRepository.countAvailableByProviderAndDate(date);
    List<AvailableProviderDTO> result = new ArrayList<>();
    for (Object[] row : localResults) {
        Long providerId = (Long) row[0];
        Long slotCount = (Long) row[1];
        try {
            ProviderDTO provider = providerServiceClient.getProvider(providerId);
            if (specialty != null && !specialty.equals(provider.getSpecialty())) continue;
            result.add(new AvailableProviderDTO(providerId, provider.getName(),
                provider.getSpecialty(), provider.getRating(), slotCount));
        } catch (FeignException.NotFound e) {
            log.warn("Provider {} not found, skipping", providerId);
        } catch (FeignException e) {
            log.warn("provider-service unavailable for {}: {}", providerId, e.getMessage());
        }
    }
    result.sort(Comparator.comparingDouble(AvailableProviderDTO::getRating).reversed());
    return result;
}
```

### 3.7 S4-F9 Refactoring — Find Idle Providers

**Current M1:** `JOIN time_slots WITH providers`.

**M3 Change:**

1. Local query: per-provider count of booked slots (`available=false`) in last `sinceDays` days, filter `bookedCount <= maxBookedSlots`
2. For each providerId, Feign → provider-service for name/specialty/rating
3. Build `List<IdleProviderDTO>`

Same Feign pattern as S4-F3. Wrap in try-catch, skip providers whose Feign call fails.

### 3.8 K8s Manifests

- `k8s/deployments/calendar-service-deployment.yaml` — readiness + liveness on `/actuator/health`
- `k8s/services/calendar-service-svc.yaml` — ClusterIP
- `k8s/configmaps/calendar-service-configmap.yaml`:

```yaml
data:
  SPRING_DATASOURCE_URL: jdbc:postgresql://calendar-postgres:5432/bookingdb-calendar
  SPRING_DATASOURCE_USERNAME: user
  SPRING_RABBITMQ_HOST: rabbitmq
  FEIGN_PROVIDER_SERVICE_URL: http://provider-service:8080
```

### 3.9 Actuator + PromQL Panels

Add to `application.yml`:

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

Choose ≥3 PromQL panels: HTTP request rate, HTTP latency percentiles, JVM health.

---

## 4. S4-INFRA — Gateway Route + Grafana + Cassandra K8s

### 4.1 Git Workflow

```
Branch: feat/M3/calendar/S4-INFRA/<studentID>

Commits:
1. feat(calendar): add calendar-service route to api-gateway application.yml (<studentID>)
2. feat(calendar): add calendar-service scrape job to prometheus-configmap.yaml (<studentID>)
3. feat(calendar): create calendar-dashboard.json with 3 LogQL + 3 PromQL panels (<studentID>)
4. feat(calendar): create Grafana K8s manifests (<studentID>)
5. feat(calendar): create Cassandra K8s manifests (<studentID>)
```

### 4.2 Gateway Route

Add to `api-gateway/src/main/resources/application.yml`:

```yaml
- id: calendar-service
  uri: http://calendar-service:8080
  predicates:
    - Path=/api/timeslots/**,/api/calendar/**
```

### 4.3 Prometheus Scrape Job

Add to `prometheus-configmap.yaml`:

```yaml
- job_name: calendar-service
  static_configs:
    - targets: ['calendar-service.booking.svc.cluster.local:8080']
  metrics_path: /actuator/prometheus
```

### 4.4 Shared Infra — Grafana K8s

This slice owns the entire `k8s/monitoring/grafana/` directory:
- `grafana-datasources.yaml` — ConfigMap pointing to Loki + Prometheus
- `grafana-dashboards.yaml` — ConfigMap embedding all 5 service dashboard JSONs
- `dashboards/calendar-dashboard.json`
- `grafana-pvc.yaml`
- `grafana-deployment.yaml` — image `grafana/grafana:10.4.2`
- `grafana-service.yaml` — NodePort 30030

### 4.5 Shared Infra — Cassandra K8s

- `k8s/statefulsets/cassandra-statefulset.yaml`
- `k8s/services/cassandra-svc.yaml`
- `k8s/pvcs/cassandra-pvc.yaml`

---

## 5. Concerns & Options

| Concern | Options | Recommendation |
|---------|---------|----------------|
| **Covering slot query semantics** | `startTime <= t AND endTime >= t` vs `startTime <= t AND endTime > t` | Use `<=` and `>=` (inclusive) — a booking at exactly slot start/end time is covered |
| **S4-F3 N+1 Feign calls** | Call provider-service per provider vs batch endpoint | Spec says batch is "recommendation, not requirement". Start with per-provider; add batch if perf matters |
| **booking.placed event — how to find matching slot?** | The event has `bookingId, userId, providerId` but NOT `appointmentDate/startTime`. | **Use Feign → booking-service** `GET /api/bookings/{bookingId}` inside the consumer. The spec (§5 line 753) explicitly lists S4 as a caller of this endpoint — this IS the intended approach. Do NOT enrich the event payload; the record definition in §2.8 is prescriptive and changing it risks grading failures. |
| **booking.cancelled — how to find the reserved slot?** | Feign-call booking-service again vs store `bookingId` in TimeSlot during `booking.placed` | **Store `bookingId` in TimeSlot** during `booking.placed`. This avoids a second Feign call during cancellation and enables O(1) lookup: `WHERE booking_id = ?`. The `BookingCancelledEvent` already carries `bookingId`. |
| **MongoDB audit — reuse existing Observer?** | Use M2's Observer pattern vs direct insert | Reuse Observer for consistency with M2 |
| **slot.reserved / slot.released** | Required or optional? | Spec says "optional" — but implement anyway for observability credit |

---

## 6. Gotchas

1. **`booking.placed` consumer requires a Feign call to booking-service** — The spec's `BookingPlacedEvent(bookingId, userId, providerId)` does NOT contain `appointmentDate` or `startTime`. The spec intentionally keeps the event lean — §5 line 753 explicitly lists S4 as a caller of `GET /api/bookings/{bookingId}`. The consumer must Feign-call booking-service to get appointment details. Do NOT modify the event record — the §2.8 definitions are prescriptive.

2. **Add `bookingId` field to `TimeSlot` entity** — Add `private Long bookingId;` (nullable, no FK constraint). Set it during `booking.placed` consumption. This enables `booking.cancelled` to find the reserved slot via `WHERE booking_id = ?` without a second Feign call. Clear it (`bookingId = null`) when releasing the slot.

3. **Remove M1 provider-existence SQL** — S4-F1, F2, F4, F8 had `SELECT COUNT(*) FROM providers WHERE id = ?` checks on the shared DB. These must be **deleted** (not replaced with Feign). The spec says "the existence check is dropped."

4. **`@EnableFeignClients(basePackages = ...)`** — Without specifying `com.team28.booking.contracts.feign`, Spring won't find the Feign interfaces in the contracts module.

5. **Jackson2JsonMessageConverter** — Must be a `@Bean`. Without it, RabbitMQ uses Java serialization and `record` classes fail.

6. **`default-requeue-rejected: false`** — Without this, failed messages loop infinitely instead of going to DLQ.

7. **Idempotent slot updates** — Always use conditional WHERE clauses (`AND available=true`/`AND available=false`) to prevent double-writes on duplicate event delivery.

8. **Don't update provider status from calendar-service** — Calendar never writes to provider-postgres. Provider status changes come from provider-service's own event consumers.

9. **MDC cleanup in finally blocks** — Every `MDC.put()` must have a corresponding `MDC.remove()` in a finally block, or IDs leak into unrelated log lines.

10. **PG17 only** — Never PG18. Cassandra K8s uses the standard Cassandra image, not a custom one.

11. **Calendar-service needs TWO Feign clients** — Both `ProviderServiceClient` (for S4-F3, S4-F9 enrichment) and `BookingServiceClient` (for consumer slot-lookup). The plan originally only had provider-service — booking-service is also required per the spec.

---

## 7. Test Scenarios

### 7.1 New Endpoint — GET /api/timeslots/provider/{id}/slot

| # | Test | Setup | Action | Expected |
|---|------|-------|--------|----------|
| T1 | Covering slot found | Slot: providerId=5, date=2026-04-22, startTime=09:30, endTime=11:30 | `GET /api/timeslots/provider/5/slot?date=2026-04-22&startTime=10:00` | 200 — returns slot DTO |
| T2 | No covering slot | No slots for provider 5 on that date | Same GET | 404 |
| T3 | Time outside window | Slot 09:30-11:30, query startTime=12:00 | GET with startTime=12:00 | 404 |
| T4 | Exact boundary — start | Query startTime=09:30 (exact slot start) | GET | 200 — slot returned |
| T5 | Exact boundary — end | Query startTime=11:30 (exact slot end) | GET | 200 — slot returned (inclusive) |
| T6 | Multiple providers, correct one | Slots for provider 5 and provider 10 | GET for provider 5 | Returns only provider 5's slot |
| T7 | Invalid date format | `date=not-a-date` | GET | 400 |

### 7.2 S4-F3 — Find Available Providers (Feign Refactor)

| # | Test | Setup | Action | Expected |
|---|------|-------|--------|----------|
| T8 | With specialty filter | 3 providers (A=Dentist r4.8, B=Dentist r4.2, C=Barber r4.5). 3 slots for A, 1 for B, 2 for C on 2026-04-15 | `GET /api/timeslots/available?date=2026-04-15&specialty=Dentist` | 200 — [A(3 slots), B(1 slot)] sorted by rating |
| T9 | No specialty filter | Same setup | `GET /api/timeslots/available?date=2026-04-15` | 200 — all 3 providers |
| T10 | No available slots | All slots booked on date | GET | 200 — empty list |
| T11 | Provider-service down | Stop provider-service | GET | Providers skipped gracefully, partial or empty result (not 500) |
| T12 | Provider deleted | Slot exists for providerId=99, provider-service returns 404 | GET | Provider 99 skipped |

### 7.3 S4-F9 — Find Idle Providers (Feign Refactor)

| # | Test | Setup | Action | Expected |
|---|------|-------|--------|----------|
| T13 | Basic filter | A=1 booked, B=5 booked, C=0 booked (last 30 days) | `GET /api/timeslots/idle?maxBookedSlots=2&sinceDays=30` | A and C returned |
| T14 | Zero threshold | Same | `maxBookedSlots=0` | Only C |
| T15 | All busy | All providers > threshold | GET | Empty list |
| T16 | Feign enrichment | Provider A exists | GET | DTO has name, specialty, rating from Feign |

### 7.4 RabbitMQ Consumers

| # | Test | Setup | Action | Expected |
|---|------|-------|--------|----------|
| T17 | booking.placed → slot booked | Slot: providerId=5, date=2026-04-22, 10:00-11:00, available=true | Publish BookingPlacedEvent(bookingId=1, userId=10, providerId=5) | Slot available=false |
| T18 | booking.placed idempotent | Same slot already available=false | Publish same event again | No change, no error |
| T19 | booking.completed → audit | Slot booked for provider 5 | Publish BookingCompletedEvent | MongoDB calendar_events has audit doc; slot stays booked |
| T20 | booking.cancelled → slot freed | Slot: available=false for provider 5 | Publish BookingCancelledEvent(bookingId=1, ...) | Slot available=true |
| T21 | booking.cancelled idempotent | Slot already available=true | Publish same cancelled event | No change |
| T22 | Malformed event → DLQ | Publish garbage payload | Consumer throws, retries 3x | Message in DLQ |
| T23 | slot.reserved published | After booking.placed processed | Check calendar.events exchange | SlotReservedEvent message present |
| T24 | slot.released published | After booking.cancelled processed | Check calendar.events exchange | SlotReleasedEvent message present |

### 7.5 Integration / End-to-End

| # | Test | Setup | Action | Expected |
|---|------|-------|--------|----------|
| T25 | Saga pre-check E2E | Booking IN_PROGRESS, provider BUSY, user ACTIVE, covering slot exists | booking-service calls calendar `GET .../slot` | 200 — saga proceeds |
| T26 | No covering slot blocks saga | Same but no matching slot | booking-service calls calendar | 404 → booking-service returns 400, no events |
| T27 | Full saga → calendar | Complete booking → booking.completed event | Calendar consumer fires, audit logged | MongoDB doc exists |
| T28 | Cancel saga → slot freed | Cancel booking → booking.cancelled event | Calendar consumer frees slot | Slot available=true |
| T29 | DB isolation verified | Run query on `providers` table from calendar-service | Direct SQL | Table doesn't exist — query fails |
| T30 | Feign + correlation ID | Set X-Correlation-ID, trigger S4-F3 | Check provider-service logs | Same correlation ID present |

---

## 8. File Checklist

### New Files

| File | Slice |
|------|-------|
| `calendar-service/.../config/FeignCorrelationConfig.java` | S4-READ-DB |
| `calendar-service/.../config/CorrelationIdFilter.java` | S4-READ-DB |
| `calendar-service/.../config/CalendarEventConfig.java` | S4-EVENTS |
| `calendar-service/.../config/RabbitConfig.java` (MessageConverter) | S4-EVENTS |
| `calendar-service/.../messaging/consumers/BookingEventConsumer.java` | S4-EVENTS |
| `calendar-service/.../messaging/publishers/SlotEventPublisher.java` | S4-EVENTS |
| `calendar-service/src/main/resources/logback-spring.xml` | S4-READ-DB |
| `k8s/secrets/calendar-postgres-secret.yaml` | S4-READ-DB |
| `k8s/pvcs/calendar-postgres-pvc.yaml` | S4-READ-DB |
| `k8s/statefulsets/calendar-postgres-statefulset.yaml` | S4-READ-DB |
| `k8s/services/calendar-postgres-svc.yaml` | S4-READ-DB |
| `k8s/deployments/calendar-service-deployment.yaml` | S4-EVENTS |
| `k8s/services/calendar-service-svc.yaml` | S4-EVENTS |
| `k8s/configmaps/calendar-service-configmap.yaml` | S4-EVENTS |
| `k8s/monitoring/grafana/dashboards/calendar-dashboard.json` | S4-INFRA |
| `k8s/monitoring/grafana/*.yaml` (6 files) | S4-INFRA |
| `k8s/statefulsets/cassandra-statefulset.yaml` | S4-INFRA |
| `k8s/services/cassandra-svc.yaml` | S4-INFRA |
| `k8s/pvcs/cassandra-pvc.yaml` | S4-INFRA |

### Modified Files

| File | Change | Slice |
|------|--------|-------|
| `calendar-service/pom.xml` | Add openfeign, amqp, contracts, loki4j deps | READ-DB + EVENTS |
| `calendar-service/.../CalendarServiceApplication.java` | Add `@EnableFeignClients` | S4-READ-DB |
| `calendar-service/.../controller/TimeSlotController.java` | Add slot lookup endpoint | S4-READ-DB |
| `calendar-service/.../service/TimeSlotService.java` | Add `findCoveringSlot`, refactor F3/F9 | READ-DB + EVENTS |
| `calendar-service/.../repository/TimeSlotRepository.java` | Add covering slot query | S4-READ-DB |
| `calendar-service/src/main/resources/application.yml` | DB URL, RabbitMQ, Feign, actuator | READ-DB + EVENTS |
| `api-gateway/.../application.yml` | Add calendar route | S4-INFRA |
| `k8s/monitoring/prometheus/prometheus-configmap.yaml` | Add scrape job | S4-INFRA |
