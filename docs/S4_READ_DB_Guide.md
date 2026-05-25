# S4-READ-DB Deep Dive — Ahmed Yasser Tawfik (calendar-service)

## Your Slice Overview

**Slice #10 — S4-READ-DB** covers:
1. Database Isolation (datasource → bookingdb-calendar)
2. New saga pre-check endpoint: `GET /api/timeslots/provider/{providerId}/slot`
3. ProviderServiceClient Feign interface with error handling
4. Kubernetes: calendar-postgres StatefulSet + PVC + Secret + headless Service
5. logback-spring.xml with Loki4J appender
6. ≥3 LogQL panels for Grafana dashboard

---

## 1. DATABASE ISOLATION

### What Changed (M1/M2 → M3)

**Before:** All services shared ONE PostgreSQL:
```
jdbc:postgresql://postgres:5432/bookingdb
```
calendar-service could SQL JOIN against provider's tables directly.

**After (M3):** calendar-service has its OWN PostgreSQL:
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://calendar-postgres:5432/bookingdb-calendar
    username: user
    password: password
```

### Why This Matters
- **No cross-service JDBC** — calendar-service CANNOT read from provider-service's database
- When calendar-service needs provider info (name, specialty, rating), it calls provider-service via **OpenFeign HTTP call** instead of SQL JOIN
- The `providerId` column in `time_slots` table is a **plain Long** — no JPA `@ManyToOne` to a Provider entity

### Your TimeSlot Entity
```java
@Entity
@Table(name = "time_slots")
public class TimeSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long providerId;     // Plain Long — NOT @ManyToOne!
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean available;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;   // PostgreSQL JSONB
    private LocalDateTime createdAt;
}
```

**Key point for evaluator:** `providerId` is a plain `Long` field. In M1 it might have been `@ManyToOne Provider provider` — that's impossible in M3 because Provider lives in a separate database. We store just the ID and look up provider details via Feign when needed.

### How Environment Variables Work
```yaml
# application.yml uses ${ENV_VAR:default_value} pattern
url: jdbc:postgresql://${POSTGRES_HOST:calendar-postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:bookingdb-calendar}
```
- In Docker Compose: env vars set in docker-compose.yaml
- In Kubernetes: env vars come from ConfigMap + Secret

---

## 2. THE SAGA PRE-CHECK ENDPOINT

### What It Does
When booking-service triggers `PUT /api/bookings/{id}/complete` (the saga), it needs to verify a covering time slot exists in the calendar. It calls YOUR endpoint:

```
GET /api/timeslots/provider/{providerId}/slot?date=2026-04-22&startTime=10:00
```

If the slot exists → returns 200 + TimeSlotDTO → saga continues
If no slot → returns 404 → booking-service aborts the saga (returns 400)

### Your Controller Code
```java
@GetMapping("/provider/{providerId}/slot")
public TimeSlotDTO getSlotForBooking(
        @PathVariable Long providerId,
        @RequestParam String date,
        @RequestParam String startTime) {
    return timeSlotService.getSlotForBooking(
        providerId, LocalDate.parse(date), LocalTime.parse(startTime));
}
```

### Your Service Code
```java
@Transactional(readOnly = true)
public TimeSlotDTO getSlotForBooking(Long providerId, LocalDate date, LocalTime startTime) {
    TimeSlot slot = timeSlotRepository
        .findByProviderIdAndDateAndStartTime(providerId, date, startTime)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "No slot found for provider " + providerId + " at " + date + " " + startTime));
    return toTimeSlotDTO(slot);
}
```

### Your Repository Method
```java
Optional<TimeSlot> findByProviderIdAndDateAndStartTime(
    Long providerId, LocalDate date, LocalTime startTime);
```
Spring Data JPA auto-generates the SQL from the method name! No `@Query` needed.

### The DTO returned (from contracts module)
```java
public record TimeSlotDTO(
    Long id, Long providerId, LocalDate date,
    LocalTime startTime, LocalTime endTime,
    boolean available, Map<String, Object> metadata
) {}
```

### Why This Endpoint Exists
The saga's 3 pre-checks before publishing `booking.completed`:
1. Feign → user-service: Is user ACTIVE? ✓
2. Feign → provider-service: Is provider BUSY? ✓
3. **Feign → calendar-service: Does covering time slot exist? ✓** ← YOUR ENDPOINT

This proves the appointment was actually scheduled in the calendar.

---

## 3. OPENFEIGN — CALLING PROVIDER-SERVICE

### What is OpenFeign?
A declarative HTTP client. You define a Java interface → Spring generates the HTTP client automatically.

### The CalendarServiceClient (YOUR service exposes to others)
```java
@FeignClient(name = "calendar-service", url = "${feign.calendar-service.url}",
             fallback = CalendarServiceClientFallback.class)
public interface CalendarServiceClient {
    @GetMapping("/api/timeslots/provider/{providerId}/slot")
    TimeSlotDTO getSlotForBooking(@PathVariable Long providerId,
                                   @RequestParam String date,
                                   @RequestParam String startTime);

    @GetMapping("/api/timeslots/provider/{providerId}/utilization")
    ProviderUtilizationDTO getProviderUtilization(@PathVariable Long providerId,
                                                   @RequestParam String startDate,
                                                   @RequestParam String endDate);
}
```

### The ProviderServiceClient (YOUR service CALLS this)
Your calendar-service calls provider-service to enrich provider details in S4-F3 and S4-F9:
```java
@FeignClient(name = "provider-service", url = "${feign.provider-service.url}")
public interface ProviderServiceClient {
    @GetMapping("/api/providers/{id}")
    ProviderDTO getProvider(@PathVariable Long id);
}
```

### How You Use It — S4-F3 (Find Available Providers)
```java
public List<AvailableProviderDTO> findAvailableProviders(LocalDate date, String specialty) {
    // Step 1: LOCAL query on calendar-postgres (no cross-service SQL!)
    List<Object[]> localResults = timeSlotRepository
        .countAvailableSlotsByProviderAndDate(date);

    List<AvailableProviderDTO> results = new ArrayList<>();
    for (Object[] row : localResults) {
        Long providerId = ((Number) row[0]).longValue();
        Long availableSlots = ((Number) row[1]).longValue();

        try {
            // Step 2: FEIGN CALL to provider-service for enrichment
            ProviderDTO provider = providerServiceClient.getProvider(providerId);

            // Step 3: Filter by specialty if provided
            if (specialty != null && !specialty.equals(provider.specialty())) {
                continue;
            }

            results.add(AvailableProviderDTO.builder()
                .providerId(providerId)
                .providerName(provider.name())
                .specialty(provider.specialty())
                .rating(provider.rating())
                .availableSlots(availableSlots)
                .build());
        } catch (FeignException.NotFound e) {
            log.warn("Provider {} not found via Feign, skipping", providerId);
        } catch (FeignException e) {
            log.warn("provider-service unavailable for {}: {}", providerId, e.getMessage());
        }
    }
    // Step 4: Sort by rating descending
    results.sort(Comparator.comparingDouble(p -> p.rating()).reversed());
    return results;
}
```

### Error Handling Pattern (IMPORTANT!)
```java
try {
    ProviderDTO provider = providerServiceClient.getProvider(providerId);
    // use it...
} catch (FeignException.NotFound e) {
    // Provider doesn't exist — skip gracefully
    log.warn("Provider {} not found via Feign, skipping", providerId);
} catch (FeignException e) {
    // provider-service is down — skip gracefully
    log.warn("provider-service unavailable: {}", e.getMessage());
}
```
**NEVER let a Feign failure crash your service!**

### Feign URL Configuration
```yaml
feign:
  provider-service:
    url: http://${FEIGN_PROVIDER_SERVICE_URL:provider-service:8080}
```
In K8s, `provider-service` resolves via DNS to the ClusterIP Service.

### Correlation ID Propagation
Every outgoing Feign call forwards `X-Correlation-ID`:
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

### @EnableFeignClients
In your main application class:
```java
@SpringBootApplication(scanBasePackages = {
    "com.team28.booking.calendar",
    "com.team28.booking.contracts.feign"
})
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class CalendarServiceApplication { }
```
This tells Spring to scan the contracts module and create proxy implementations for all `@FeignClient` interfaces.

---

## 4. KUBERNETES — CALENDAR-POSTGRES INFRASTRUCTURE

### What You Deployed

4 YAML files for the calendar-postgres database:

#### 4a. Secret (stores credentials)
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: calendar-postgres-secret
  namespace: booking
type: Opaque
stringData:
  POSTGRES_DB: bookingdb-calendar
  POSTGRES_USER: user
  POSTGRES_PASSWORD: password
```
**Why Secret not ConfigMap?** Passwords are sensitive. Secrets are base64-encoded and can be restricted by RBAC.

#### 4b. PersistentVolumeClaim (disk storage)
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: calendar-postgres-pvc
  namespace: booking
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
```
**Why PVC?** Without persistent storage, all data is lost when the pod restarts. PVC ensures PostgreSQL data survives pod restarts.

#### 4c. StatefulSet (the database pod)
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: calendar-postgres
  namespace: booking
spec:
  serviceName: calendar-postgres
  replicas: 1
  selector:
    matchLabels:
      app: calendar-postgres
  template:
    spec:
      containers:
        - name: calendar-postgres
          image: postgres:17          # PostgreSQL 17, NOT 18!
          ports:
            - containerPort: 5432
          envFrom:
            - secretRef:
                name: calendar-postgres-secret
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "user"]
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "user"]
          volumeMounts:
            - name: calendar-postgres-data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: calendar-postgres-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```

**Why StatefulSet (not Deployment)?**
- Databases are **stateful** — they need stable network identity and persistent storage
- StatefulSet provides stable pod names (`calendar-postgres-0`)
- VolumeClaimTemplates automatically create a PVC per replica

**Why postgres:17?**
PostgreSQL 18 breaks Hibernate native query implicit cast operator resolution. Critical rule #5.

**Readiness/Liveness probes:**
- `pg_isready -U user` checks if PostgreSQL is accepting connections
- Readiness: "Can I receive traffic?" — K8s won't route to it until ready
- Liveness: "Am I still alive?" — K8s restarts the pod if this fails

#### 4d. Headless Service (DNS resolution)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: calendar-postgres
  namespace: booking
spec:
  clusterIP: None        # ← Makes it "headless"
  selector:
    app: calendar-postgres
  ports:
    - port: 5432
```
**Why headless (clusterIP: None)?**
For StatefulSets, headless services provide direct DNS resolution to specific pods. `calendar-postgres` resolves to the StatefulSet pod IP directly.

**How calendar-service finds it:**
In `application.yml`: `url: jdbc:postgresql://calendar-postgres:5432/bookingdb-calendar`
K8s DNS resolves `calendar-postgres` → the headless service → the postgres pod.

---

## 5. LOGBACK-SPRING.XML — STRUCTURED LOGGING TO LOKI

### Your Configuration
```xml
<configuration>
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>http://${LOKI_HOST:-loki.monitoring.svc.cluster.local}:3100/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <pattern>app=booking,service=${appName},level=%level,env=k8s</pattern>
            </label>
            <message>
                <pattern>{"timestamp":"%d{ISO8601}","level":"%level","service":"${appName}",
                "correlationId":"%X{correlationId:-}","slotId":"%X{slotId:-}",
                "providerId":"%X{providerId:-}","bookingId":"%X{bookingId:-}",
                "routingKey":"%X{routingKey:-}","message":"%msg"}</pattern>
            </message>
        </format>
    </appender>
</configuration>
```

### How It Works
1. **Loki4J appender** — pushes JSON logs over HTTP to Loki
2. **Labels** — `app=booking, service=calendar-service, level=INFO` — Loki indexes these for fast queries
3. **Message** — JSON with MDC fields: `correlationId`, `slotId`, `providerId`, `bookingId`, `routingKey`
4. **MDC (Mapped Diagnostic Context)** — thread-local key-value store. Your `CorrelationIdFilter` puts the correlation ID into MDC:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");  // Prevent leaking to next request!
        }
    }
}
```

### Data Flow
```
calendar-service logs → Loki4J appender → HTTP POST → Loki (monitoring namespace)
                                                        ↓
                                                    Grafana queries Loki with LogQL
```

---

## 6. GRAFANA DASHBOARD — LogQL PANELS

Your dashboard has 3 LogQL panels:

### Panel 1: Error Rate
```
sum(rate({service="calendar-service"} |= "ERROR" [5m]))
```
- `{service="calendar-service"}` — label filter: select calendar-service logs
- `|= "ERROR"` — line filter: only lines containing "ERROR"
- `rate(...[5m])` — calculate the per-second rate over 5-minute windows
- `sum(...)` — aggregate all streams

### Panel 2: Feign Call Outcomes
```
{service="calendar-service"} |~ "Feign (GET|POST)"
```
- `|~` — regex line filter
- Shows all Feign call logs — both successes and failures

### Panel 3: RabbitMQ Event Audit
```
{service="calendar-service"} |~ "(booking\\.(completed|cancelled))"
```
- Shows logs related to consuming booking events
- Helps verify the saga is flowing correctly through calendar-service

### LogQL 3-Layer Structure (KNOW THIS!)
1. **Label selector:** `{app="booking", service="calendar-service", level="ERROR"}`
2. **Line filter:** `|= "text"` (exact) or `|~` (regex) or `| json` (parse JSON)
3. **Aggregation:** `count_over_time(... [1m])`, `rate(... [5m])`, `sum by (service) (...)`

---

## 7. ADDITIONAL CONCEPTS YOU MUST KNOW

### Actuator + Prometheus Metrics
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
- Exposes `/actuator/prometheus` — Prometheus scrapes this every 15s
- `percentiles-histogram: true` — enables P50/P95/P99 latency calculation

### Circuit Breaker (Bonus)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      provider-service:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
```
If 50% of the last 5 calls to provider-service fail, the circuit **opens** — subsequent calls immediately return the fallback without even trying. After 5 seconds, it enters **half-open** state and tries 2 calls to see if the service recovered.

### RabbitMQ Configuration
```yaml
spring:
  rabbitmq:
    host: rabbitmq
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
```
- `auto` ACK: Spring acknowledges messages when listener returns successfully
- `default-requeue-rejected: false`: Failed messages go to DLQ, not back in the queue
- `max-attempts: 3`: Retry 3 times before sending to DLQ

---

## 8. LIKELY EVALUATOR QUESTIONS & ANSWERS

**Q: Why does calendar-service need its own PostgreSQL?**
A: Database isolation — true microservices principle. Each service owns its data. calendar-service can't SQL JOIN against provider-service's tables. This enables independent deployment and scaling.

**Q: How does your saga pre-check endpoint work?**
A: When booking-service calls `PUT /api/bookings/{id}/complete`, it Feign-calls my endpoint `GET /api/timeslots/provider/{providerId}/slot?date=...&startTime=...`. I query my local calendar-postgres for a matching time slot. If found → 200 + TimeSlotDTO. If not → 404. This proves the appointment was actually scheduled.

**Q: What happens if provider-service is down when you call it?**
A: Feign throws FeignException. I wrap every call in try-catch. On FeignException.NotFound → skip that provider. On general FeignException → log a warning and skip. The service never crashes.

**Q: Why StatefulSet and not Deployment for PostgreSQL?**
A: Databases are stateful — they need stable pod identity and persistent storage. StatefulSet provides both. A Deployment's pods are interchangeable; StatefulSet pods have stable names (calendar-postgres-0) and their own PVCs.

**Q: What is a headless Service?**
A: A Service with `clusterIP: None`. Instead of load-balancing, it returns the pod IPs directly via DNS. Used with StatefulSets so the application can connect directly to a specific database pod.

**Q: Explain your logback-spring.xml.**
A: It configures the Loki4J appender to push JSON-structured logs to Loki at `loki.monitoring.svc.cluster.local:3100`. Each log line includes MDC fields (correlationId, slotId, providerId, bookingId, routingKey) so I can filter and trace requests across services in Grafana.

**Q: What is MDC and why do you clear it in finally?**
A: MDC (Mapped Diagnostic Context) is a thread-local Map that attaches context to log lines. CorrelationIdFilter puts the correlation ID into MDC. I clear it in `finally` to prevent the ID from leaking to the next HTTP request that reuses the same thread.

**Q: What are your 3 LogQL panels?**
A: (1) Error rate — counts ERROR logs per 5 minutes to detect spikes. (2) Feign call outcomes — shows successful vs failed Feign calls to provider-service. (3) RabbitMQ event audit — shows booking.completed and booking.cancelled events consumed by calendar-service.

**Q: How does Feign know the URL of provider-service?**
A: `@FeignClient(url = "${feign.provider-service.url}")` reads from application.yml. In K8s, this resolves to `http://provider-service:8080` via Kubernetes DNS. The ConfigMap sets the env var.

**Q: What is @EnableFeignClients?**
A: It tells Spring to scan for interfaces annotated with `@FeignClient` and create proxy implementations. At runtime, calling a method on the interface triggers an HTTP request to the target service.
