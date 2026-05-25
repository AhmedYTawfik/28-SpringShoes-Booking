# S4-EVENTS Deep Dive — Ahmed Yasser Tawfik (calendar-service)

## Your Slice Overview

**Slice #11 — S4-EVENTS** covers:
1. `calendar.events` TopicExchange + publishers (`slot.reserved`, `slot.released`)
2. Consumer queue `calendar.booking.saga-listener` + DLQ
3. Consumers for `booking.placed` / `booking.completed` / `booking.cancelled`
4. calendar-service K8s Deployment + ClusterIP Service + ConfigMap
5. Actuator config (prometheus + health endpoints)
6. ≥3 PromQL panels in Grafana dashboard
7. S4-F3 and S4-F9 Java refactor (Feign replaces SQL JOIN)

---

## 1. RABBITMQ TOPOLOGY — What You Built

### Concept: What is a TopicExchange?
A TopicExchange is a RabbitMQ router. When a message is published with a routing key (e.g., `booking.placed`), the exchange checks all bindings and delivers the message to queues whose binding key matches.

### Concept: What is a Queue?
A queue is a mailbox that holds messages until a consumer reads them. Each service has its own queue so messages are delivered independently.

### Concept: What is a Binding?
A binding is a RULE that says: "When a message with routing key X arrives at exchange Y, send it to queue Z."

### Concept: What is a DLQ (Dead Letter Queue)?
When a consumer fails to process a message after 3 retries, the message goes to the DLQ instead of being lost. It's a "graveyard" for failed messages that you can inspect later.

### Your Topology Diagram

```
booking-service publishes to:
  Exchange: booking.events (TopicExchange)
    ├── routing key: booking.placed    ──→ your queue: calendar.booking.saga-listener
    ├── routing key: booking.completed ──→ your queue: calendar.booking.saga-listener
    └── routing key: booking.cancelled ──→ your queue: calendar.booking.saga-listener

Your calendar-service publishes to:
  Exchange: calendar.events (TopicExchange)
    ├── routing key: slot.reserved   (observability only)
    └── routing key: slot.released   (observability only)

If your consumer FAILS 3 times:
  calendar.booking.saga-listener ──→ DLX: calendar.dlx ──→ DLQ: calendar.booking.saga-listener.dlq
```

### Your Code — CalendarEventConfig.java

```java
@Configuration
public class CalendarEventConfig {

    // YOUR exchange — for publishing slot events
    @Bean
    public TopicExchange calendarEventsExchange() {
        return new TopicExchange("calendar.events");
    }

    // REFERENCE to booking-service's exchange — so you can bind to it
    @Bean
    public TopicExchange calendarBookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    // YOUR consumer queue — receives booking events
    @Bean
    public Queue calendarBookingSagaQueue() {
        return QueueBuilder.durable("calendar.booking.saga-listener")
            .withArgument("x-dead-letter-exchange", "calendar.dlx")           // Failed → DLX
            .withArgument("x-dead-letter-routing-key", "calendar.booking.saga-listener.dlq")
            .build();
    }

    // Dead letter exchange
    @Bean
    public TopicExchange calendarDlx() {
        return new TopicExchange("calendar.dlx");
    }

    // Dead letter queue
    @Bean
    public Queue calendarBookingSagaDlq() {
        return QueueBuilder.durable("calendar.booking.saga-listener.dlq").build();
    }

    // Bind DLQ to DLX
    @Bean
    public Binding calendarDlqBinding() {
        return BindingBuilder.bind(calendarBookingSagaDlq())
            .to(calendarDlx()).with("calendar.booking.saga-listener.dlq");
    }

    // Bind YOUR queue to booking.events exchange for 3 routing keys
    @Bean
    public Binding calendarBookingPlacedBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue())
            .to(calendarBookingEventsExchange()).with("booking.placed");
    }
    @Bean
    public Binding calendarBookingCompletedBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue())
            .to(calendarBookingEventsExchange()).with("booking.completed");
    }
    @Bean
    public Binding calendarBookingCancelledBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue())
            .to(calendarBookingEventsExchange()).with("booking.cancelled");
    }

    // JSON serializer — events are sent/received as JSON
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

### Why `durable("calendar.booking.saga-listener")`?
Durable = survives RabbitMQ restart. Non-durable queues are deleted when RabbitMQ restarts, losing all messages.

### Why Jackson2JsonMessageConverter?
Events are Java records (e.g., `BookingPlacedEvent`). Jackson converts them to JSON when publishing and back to Java when consuming. Without this, RabbitMQ uses Java serialization (ugly and fragile).

---

## 2. EVENT CONSUMERS — What Happens When Events Arrive

### Your Code — CalendarBookingEventListener.java

```java
@Service
@RabbitListener(queues = "calendar.booking.saga-listener")  // Listen on this queue
public class CalendarBookingEventListener {
```

`@RabbitListener` tells Spring: "This class listens on the queue. Route incoming messages to the appropriate `@RabbitHandler` method based on the message type."

### Consumer 1: booking.placed → Mark Slot as BOOKED

When a new booking is placed, reserve the time slot so no one else can book it.

```java
@RabbitHandler
public void handleBookingPlaced(BookingPlacedEvent event) {
    log.info("Received booking.placed: bookingId={} providerId={}", 
             event.bookingId(), event.providerId());

    // Step 1: Feign call to booking-service to get appointment date/time
    BookingDTO booking = fetchBooking(event.bookingId());
    if (booking == null) return;

    // Step 2: Atomic UPDATE — idempotent!
    int updated = timeSlotRepository.reserveSlot(
        event.providerId(), booking.appointmentDate(), booking.startTime());

    if (updated == 0) {
        log.info("Slot already reserved — idempotent skip");
        return;  // Duplicate event → safe no-op
    }

    // Step 3: Publish slot.reserved (optional, for observability)
    timeSlotRepository.findByProviderIdAndDateAndStartTime(...)
        .ifPresent(slot -> publisher.publishSlotReserved(
            slot.getId(), event.providerId(), event.bookingId()));
}
```

The SQL behind `reserveSlot()`:
```sql
UPDATE time_slots SET available = false
WHERE provider_id = :providerId AND date = :date AND start_time = :startTime
  AND available = true    -- Idempotency guard! Only updates if currently available
```

### Consumer 2: booking.cancelled → FREE the Slot

When a booking is cancelled, release the time slot so others can book it.

```java
@RabbitHandler
public void handleBookingCancelled(BookingCancelledEvent event) {
    log.info("Received booking.cancelled: bookingId={} providerId={}", 
             event.bookingId(), event.providerId());

    BookingDTO booking = fetchBooking(event.bookingId());
    if (booking == null) return;

    // Atomic UPDATE — idempotent!
    int updated = timeSlotRepository.releaseSlot(
        event.providerId(), booking.appointmentDate(), booking.startTime());

    if (updated == 0) {
        log.info("Slot already available — idempotent skip");
        return;
    }

    // Publish slot.released (optional)
    publisher.publishSlotReleased(slot.getId(), event.providerId(), event.bookingId());
}
```

The SQL behind `releaseSlot()`:
```sql
UPDATE time_slots SET available = true
WHERE provider_id = :providerId AND date = :date AND start_time = :startTime
  AND available = false   -- Only updates if currently booked
```

### Consumer 3: booking.completed → Audit Log (MongoDB)

When a booking is completed, just log it for audit purposes. The slot stays booked.

```java
@RabbitHandler
public void handleBookingCompleted(BookingCompletedEvent event) {
    log.info("Received booking.completed: bookingId={} providerId={}", 
             event.bookingId(), event.providerId());

    Map<String, Object> payload = new HashMap<>();
    payload.put("bookingId", event.bookingId());
    payload.put("userId", event.userId());
    payload.put("providerId", event.providerId());
    payload.put("totalPrice", event.totalPrice());
    timeSlotService.fireEvent("TRIP_COMPLETED", payload);  // → MongoDB Observer
}
```

No database UPDATE — just fires the Observer pattern to log to MongoDB's `calendar_events` collection.

### Concept: Idempotent Consumers — CRITICAL

RabbitMQ delivers at-least-once. The same event might arrive TWICE. Your consumers MUST handle duplicates safely:

```sql
-- reserveSlot: "UPDATE ... WHERE available = true"
-- If the slot is ALREADY reserved (available=false), the WHERE clause
-- doesn't match → returns 0 → consumer logs "idempotent skip" → no-op
```

This is a STATE-BASED GUARD: the UPDATE only succeeds if the data is in the expected state.

### Error Handling — fetchBooking helper

```java
private BookingDTO fetchBooking(Long bookingId) {
    try {
        return bookingServiceClient.getBooking(bookingId);  // Feign call
    } catch (FeignException.NotFound e) {
        log.warn("Booking {} not found — skipping slot update", bookingId);
        return null;  // Skip gracefully
    } catch (FeignException e) {
        log.error("booking-service unavailable: {}", e.getMessage());
        throw new RuntimeException("booking-service unavailable", e);
        // ↑ Re-throw → Spring AMQP retries 3 times → then sends to DLQ
    }
}
```

Key difference:
- **404 (NotFound):** Skip the event gracefully — booking doesn't exist, nothing to do
- **503/timeout (service down):** RE-THROW so Spring retries. After 3 retries → DLQ

---

## 3. EVENT PUBLISHERS — slot.reserved / slot.released

```java
@Service
public class CalendarEventPublisher {
    private static final String EXCHANGE = "calendar.events";
    private final RabbitTemplate rabbitTemplate;

    public void publishSlotReserved(Long slotId, Long providerId, Long bookingId) {
        SlotReservedEvent event = new SlotReservedEvent(slotId, providerId, bookingId);
        rabbitTemplate.convertAndSend(EXCHANGE, "slot.reserved", event);
        //                            ↑ exchange  ↑ routing key  ↑ payload
    }

    public void publishSlotReleased(Long slotId, Long providerId, Long bookingId) {
        SlotReleasedEvent event = new SlotReleasedEvent(slotId, providerId, bookingId);
        rabbitTemplate.convertAndSend(EXCHANGE, "slot.released", event);
    }
}
```

Event payloads (from contracts module):
```java
public record SlotReservedEvent(Long slotId, Long providerId, Long bookingId) {}
public record SlotReleasedEvent(Long slotId, Long providerId, Long bookingId) {}
```

These are OPTIONAL — for observability/audit only. No other service consumes them in the current architecture.

---

## 4. KUBERNETES — calendar-service Deployment + Service + ConfigMap

### 4a. ConfigMap (non-sensitive config)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: calendar-service-config
  namespace: booking
data:
  POSTGRES_HOST: calendar-postgres          # DB host (K8s DNS name)
  POSTGRES_PORT: "5432"
  POSTGRES_DB: bookingdb-calendar           # Isolated DB name
  RABBITMQ_HOST: rabbitmq                   # RabbitMQ DNS name
  RABBITMQ_PORT: "5672"
  LOKI_HOST: loki.monitoring.svc.cluster.local  # Cross-namespace!
  FEIGN_USER_SERVICE_URL: user-service:8080     # Feign targets
  FEIGN_PROVIDER_SERVICE_URL: provider-service:8080
  FEIGN_BOOKING_SERVICE_URL: booking-service:8080
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: prometheus,health,info
```

Why ConfigMap? Stores non-sensitive config as environment variables. The container reads `$POSTGRES_HOST` etc. at startup.

Why `loki.monitoring.svc.cluster.local`? Loki is in the `monitoring` namespace, not `booking`. Cross-namespace DNS requires the full name.

### 4b. Deployment (runs the app containers)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: calendar-service
  namespace: booking
spec:
  replicas: 2                 # 2 pods for high availability
  selector:
    matchLabels:
      app: calendar-service
  template:
    spec:
      containers:
        - name: calendar-service
          image: team28/calendar-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: calendar-service-config    # Load ConfigMap as env vars
            - secretRef:
                name: calendar-service-secret    # Load Secret as env vars
          readinessProbe:
            httpGet:
              path: /actuator/health       # K8s checks this
              port: 8080
            initialDelaySeconds: 30        # Wait 30s before first check
            periodSeconds: 10              # Check every 10s
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60        # Wait 60s (Spring takes time to start)
            periodSeconds: 20
          resources:
            requests:
              cpu: "250m"                  # Minimum guaranteed CPU
              memory: "512Mi"
            limits:
              cpu: "1"                     # Maximum allowed CPU
              memory: "1Gi"
```

**replicas: 2** — Always 2 pods running. If one crashes, the other serves traffic.

**readinessProbe** — "Am I ready to receive traffic?" K8s won't send requests until this passes. Uses `/actuator/health` (Spring Boot health endpoint).

**livenessProbe** — "Am I still alive?" If this fails, K8s RESTARTS the pod. Uses the same `/actuator/health`.

**resources.requests** — Minimum CPU/RAM guaranteed. K8s uses this for scheduling decisions.
**resources.limits** — Maximum CPU/RAM allowed. If exceeded, K8s throttles (CPU) or kills (RAM) the pod.

### 4c. ClusterIP Service (internal networking)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: calendar-service
  namespace: booking
spec:
  selector:
    app: calendar-service    # Routes to pods with this label
  ports:
    - name: http
      port: 8080
      targetPort: 8080
  type: ClusterIP            # Only reachable inside the cluster
```

ClusterIP = internal DNS name. Other services call `http://calendar-service:8080` and K8s load-balances across the 2 pods.

---

## 5. ACTUATOR — Exposing Health and Metrics

```yaml
# In application.yml
management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health,info"
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true   # Enable P50/P95/P99 latency
```

This exposes 3 endpoints:
- `/actuator/health` — used by K8s probes + human debugging
- `/actuator/prometheus` — Prometheus scrapes this for metrics
- `/actuator/info` — app info

`percentiles-histogram: true` enables histogram buckets so Prometheus can calculate P50/P95/P99 latency percentiles.

---

## 6. PROMQL PANELS — Grafana Dashboard

Your dashboard has 3 PromQL panels:

### Panel 4: HTTP Request Rate
```
sum(rate(http_server_requests_seconds_count{job="calendar-service"}[5m])) by (uri, status)
```
- `http_server_requests_seconds_count` — Spring Boot auto-metric: total request count
- `rate(...[5m])` — per-second rate over 5-minute windows
- `by (uri, status)` — group by endpoint path and HTTP status code
- Shows: how many requests/second per endpoint (200s vs 404s vs 500s)

### Panel 5: Latency P50/P95/P99
```
histogram_quantile(0.95, sum(rate(
    http_server_requests_seconds_bucket{job="calendar-service",uri=~"/api/timeslots/.*"}[5m]
)) by (le))
```
- `http_server_requests_seconds_bucket` — histogram of request durations
- `histogram_quantile(0.95, ...)` — 95th percentile: "95% of requests complete within X seconds"
- P50 = median, P95 = 95th percentile, P99 = 99th percentile
- Shows: if P99 spikes, you have slow outlier requests

### Panel 6: HikariCP Connection Pool
```
hikaricp_connections_active{job="calendar-service"}
hikaricp_connections_pending{job="calendar-service"}
hikaricp_connections{job="calendar-service"}
```
- Active = connections currently in use by queries
- Pending = threads waiting for a connection (BAD if high → pool exhaustion)
- Total = all connections in the pool
- Shows: is your database connection pool healthy?

### PromQL Syntax Cheat Sheet
| Function | What it does |
|----------|-------------|
| `rate(metric[5m])` | Per-second increase rate over 5 minutes |
| `sum(...) by (label)` | Aggregate and group by label |
| `histogram_quantile(0.95, ...)` | Calculate percentile from histogram |
| `{job="calendar-service"}` | Label filter: select only calendar-service metrics |
| `{uri=~"/api/timeslots/.*"}` | Regex label filter |

---

## 7. S4-F3 and S4-F9 JAVA REFACTOR

### What Changed: SQL JOIN → Feign Call

**BEFORE (M1/M2) — S4-F3 Find Available Providers:**
```sql
-- ONE query across shared database
SELECT p.id, p.name, p.specialty, p.rating, COUNT(ts.id) AS availableSlots
FROM time_slots ts
JOIN providers p ON ts.provider_id = p.id    -- CROSS-SERVICE JOIN!
WHERE ts.date = :date AND ts.available = true
  AND (:specialty IS NULL OR p.specialty = :specialty)
GROUP BY p.id, p.name, p.specialty, p.rating
ORDER BY p.rating DESC
```

**AFTER (M3) — Two-step: local query + Feign calls:**
```java
// Step 1: LOCAL query on calendar-postgres only
List<Object[]> localResults = timeSlotRepository
    .countAvailableSlotsByProviderAndDate(date);
// Returns: [(providerId=7, count=3), (providerId=9, count=1)]

// Step 2: For each providerId, FEIGN CALL to provider-service
for (Object[] row : localResults) {
    Long providerId = ((Number) row[0]).longValue();
    try {
        ProviderDTO provider = providerServiceClient.getProvider(providerId);
        // Step 3: Filter by specialty if needed
        if (specialty != null && !specialty.equals(provider.specialty())) continue;
        // Step 4: Build the DTO with enriched data
        results.add(AvailableProviderDTO.builder()
            .providerId(providerId)
            .providerName(provider.name())      // From Feign
            .specialty(provider.specialty())     // From Feign
            .rating(provider.rating())           // From Feign
            .availableSlots(availableSlots)       // From local query
            .build());
    } catch (FeignException.NotFound e) {
        log.warn("Provider {} not found, skipping", providerId);
    } catch (FeignException e) {
        log.warn("provider-service unavailable: {}", e.getMessage());
    }
}
// Step 5: Sort by rating descending
results.sort(Comparator.comparingDouble(p -> p.rating()).reversed());
```

**S4-F9 Find Idle Providers — Same pattern:**
1. Local query: count booked slots per provider → `(providerId, bookedCount, totalCount)`
2. Filter by `bookedCount <= maxBookedSlots`
3. Feign call per provider for name/specialty/rating enrichment
4. Build `IdleProviderDTO` list

---

## 8. EVENT FLOW — How It All Connects

### Scenario: User Books an Appointment

```
1. User calls POST /api/bookings → booking-service creates booking (REQUESTED)
2. booking-service publishes: booking.placed to booking.events exchange
3. Exchange routes to: calendar.booking.saga-listener queue
4. YOUR consumer picks it up: handleBookingPlaced()
5. YOU Feign-call booking-service: GET /api/bookings/{id} → get date/time
6. YOU run: UPDATE time_slots SET available=false WHERE ... AND available=true
7. YOU publish: slot.reserved to calendar.events (optional)
```

### Scenario: Booking Completed (Happy Path)

```
1. Admin calls PUT /api/bookings/{id}/complete → booking-service publishes booking.completed
2. YOUR consumer: handleBookingCompleted()
3. YOU fire Observer: TRIP_COMPLETED → logged to MongoDB calendar_events
4. Time slot stays booked (available=false) — appointment was fulfilled
```

### Scenario: Payment Fails → Booking Cancelled (Compensation)

```
1. Payment fails → booking-service publishes booking.cancelled
2. YOUR consumer: handleBookingCancelled()
3. YOU Feign-call booking-service for date/time
4. YOU run: UPDATE time_slots SET available=true WHERE ... AND available=false
5. YOU publish: slot.released to calendar.events (optional)
6. Slot is now FREE for someone else to book!
```

---

## 9. LIKELY EVALUATOR QUESTIONS & ANSWERS (30 Questions)

**Q1: What is a TopicExchange?**
A: A RabbitMQ exchange that routes messages based on routing keys with wildcard support. `booking.*` matches `booking.placed`, `booking.completed`, etc.

**Q2: Why do you declare `booking.events` exchange in your config?**
A: So I can bind my queue to it. The exchange is "owned" by booking-service, but I need a reference to create bindings. RabbitMQ creates exchanges idempotently — if it already exists, the declaration is a no-op.

**Q3: What routing keys does your queue listen to?**
A: Three: `booking.placed`, `booking.completed`, `booking.cancelled`. Each has a separate Binding bean.

**Q4: What is x-dead-letter-exchange?**
A: A queue argument that tells RabbitMQ: "When a message is rejected (consumer fails after all retries), send it to this exchange instead of dropping it."

**Q5: What happens when your consumer throws an exception?**
A: Spring AMQP retries 3 times (configured in application.yml). If all 3 fail, the message is rejected with `default-requeue-rejected: false`, which triggers the DLQ routing via `x-dead-letter-exchange`.

**Q6: What does `acknowledge-mode: auto` mean?**
A: Spring automatically ACKs (acknowledges) the message when the listener method returns normally. If it throws, Spring rejects the message. No manual `basicAck`/`basicNack` calls needed.

**Q7: What does `default-requeue-rejected: false` mean?**
A: When a message is rejected (consumer throws after all retries), do NOT put it back in the queue. Instead, route it to the DLQ. Without this, failed messages would loop forever.

**Q8: What is Jackson2JsonMessageConverter?**
A: Converts Java objects to JSON when publishing and JSON back to Java when consuming. Without it, Spring uses Java serialization which is fragile and unreadable.

**Q9: How does @RabbitHandler know which method to call?**
A: By the MESSAGE TYPE. Spring deserializes the JSON and matches it to the method parameter type. `BookingPlacedEvent` → `handleBookingPlaced()`, `BookingCompletedEvent` → `handleBookingCompleted()`.

**Q10: What makes your consumers idempotent?**
A: State-based guards in SQL. `reserveSlot` uses `WHERE available = true`. If the slot is already reserved (duplicate event), the WHERE clause doesn't match, UPDATE returns 0, and the consumer skips it safely.

**Q11: Why do you Feign-call booking-service inside the consumer?**
A: The event payload (`BookingPlacedEvent`) only contains `bookingId`, `userId`, `providerId`. You need `appointmentDate` and `startTime` to find the correct time slot. Those fields come from booking-service via Feign.

**Q12: What happens if booking-service is down when your consumer runs?**
A: The `fetchBooking` helper re-throws RuntimeException. Spring AMQP catches it, retries up to 3 times. If still down after 3 retries → message goes to DLQ.

**Q13: What happens if booking-service returns 404?**
A: `fetchBooking` catches `FeignException.NotFound`, logs a warning, returns null. The consumer returns without doing anything. The message is ACKed (consumed successfully — nothing to do).

**Q14: What does booking.completed do in calendar-service?**
A: Just fires an Observer event (TRIP_COMPLETED) which logs to MongoDB. NO database update — the time slot stays booked (available=false). The appointment happened, so the slot should remain occupied.

**Q15: What does booking.cancelled do in calendar-service?**
A: Releases the time slot by setting `available = true`. This is COMPENSATION — undoing what `booking.placed` did. The slot is now free for someone else.

**Q16: What is the difference between ClusterIP and NodePort?**
A: ClusterIP = only reachable inside the cluster (used for service-to-service). NodePort = exposes on a specific port on the host machine (used for external access like api-gateway).

**Q17: Why replicas: 2 in the Deployment?**
A: High availability. If one pod crashes, the other still serves traffic. K8s automatically restarts the crashed pod.

**Q18: What does envFrom: configMapRef do?**
A: Loads ALL key-value pairs from the ConfigMap as environment variables into the container. So `POSTGRES_HOST: calendar-postgres` becomes `$POSTGRES_HOST=calendar-postgres` inside the container.

**Q19: Why is LOKI_HOST a full DNS name?**
A: Because Loki is in the `monitoring` namespace, not `booking`. Cross-namespace DNS requires the full form: `loki.monitoring.svc.cluster.local`. Within the same namespace, just the service name works.

**Q20: What is readinessProbe used for?**
A: K8s checks `/actuator/health` every 10 seconds. If it fails, K8s removes the pod from the Service's endpoint list — no traffic is routed to it. Once it passes again, traffic resumes.

**Q21: What is livenessProbe used for?**
A: If `/actuator/health` fails 5 consecutive times (failureThreshold: 5), K8s KILLS and RESTARTS the pod. It's the "are you still alive?" check.

**Q22: Why initialDelaySeconds: 60 for liveness?**
A: Spring Boot takes time to start (loading context, connecting to DBs, etc.). If you check too early, the pod isn't ready yet and K8s would restart it in a loop.

**Q23: What does resources.requests.cpu: 250m mean?**
A: 250 millicores = 0.25 CPU cores. This is the GUARANTEED minimum. K8s won't schedule the pod on a node that doesn't have at least 250m available.

**Q24: What does /actuator/prometheus expose?**
A: Metrics in Prometheus text format. Example: `http_server_requests_seconds_count{uri="/api/timeslots",status="200"} 1547`. Prometheus scrapes this every 15 seconds.

**Q25: What does percentiles-histogram: true enable?**
A: Generates histogram buckets for request durations. This allows Prometheus to calculate P50/P95/P99 latencies using `histogram_quantile()`.

**Q26: What does rate() do in PromQL?**
A: Calculates the per-second increase rate of a counter metric over a time window. `rate(metric[5m])` = "how many per second, averaged over the last 5 minutes."

**Q27: What does histogram_quantile(0.95, ...) mean?**
A: "95% of requests complete within this many seconds." P95=0.2s means 95% of requests finish in under 200ms. The remaining 5% are slower.

**Q28: Why monitor HikariCP connection pool?**
A: If `pending > 0`, threads are WAITING for a database connection. This means your pool is too small or queries are too slow. It's a critical early warning for performance problems.

**Q29: What is the difference between S4-F3 before and after M3?**
A: Before: one SQL JOIN across `time_slots` and `providers` tables (shared DB). After: local query on `time_slots` (calendar-postgres) + Feign call per provider to provider-service for name/specialty/rating.

**Q30: Why sort by rating descending in S4-F3?**
A: So the best-rated providers appear first in the list. Users see the highest-quality providers at the top.
