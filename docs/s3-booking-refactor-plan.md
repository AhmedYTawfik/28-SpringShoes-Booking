# S3 — Booking Service Refactor: Implementation & Review Plan

## Current State Summary

**Already done (Sections 1 & 2):**
- DB isolation (`bookingdb-bookings`), Feign deps, `@EnableFeignClients`, RabbitMQ config
- Contracts module with all Feign clients & event records
- `BookingEventConfig` (exchanges, queues, DLQ, bindings)
- `BookingEventPublisher` (placed/completed/cancelled)
- `BookingPaymentEventListener` (initiated/completed/failed/refunded consumers)
- Saga status enum values already in `Booking.Status`

**What Section 5 requires (remaining work):**
1. 6 new REST endpoints for Feign consumers (summary, active-count, completed-count × user & provider)
2. Refactor S3-F2 (assign provider) → Feign + publish `booking.placed`
3. Refactor S3-F4 (complete booking) → 3 pre-saga Feign checks + publish `booking.completed`
4. Refactor S3-F7 (cancel booking) → remove direct provider update + publish `booking.cancelled`
5. Refactor S3-F11 (record interaction) → Feign for user/provider data
6. Refactor S3-F12 (recommendations) → Feign for user/provider data
7. Refactor S3-F10 (analytics dashboard) → Feign batch to invoice-service
8. Remove cross-service native queries from `BookingRepository`

---

## Agent Distribution

### Agent A — New Endpoints + S3-F2 + S3-F7 + S3-F10

### Agent B — S3-F4 (Saga Trigger) + S3-F11 + S3-F12 + Repository Cleanup

---

## Agent A: Detailed Tasks

### A1. New Feign-callable Endpoints (Controller + Service + Repository)

Add to `BookingController.java` — 6 new `@GetMapping` methods:

```java
// --- User-scoped ---
@GetMapping("/user/{userId}/summary")       → BookingSummaryDTO
@GetMapping("/user/{userId}/active-count")  → int
@GetMapping("/user/{userId}/completed-count") → long

// --- Provider-scoped ---
@GetMapping("/provider/{providerId}/summary") → ProviderBookingSummaryDTO
  // accepts optional ?startDate=&endDate= query params
@GetMapping("/provider/{providerId}/active-count") → int
@GetMapping("/provider/{providerId}/completed-count") → long
```

**Repository queries to add to `BookingRepository.java`:**

```java
// User summary: count total, completed, cancelled; sum totalPrice; avg totalPrice
@Query(value = "SELECT COUNT(*), " +
  "COUNT(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN 1 END), " +
  "COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END), " +
  "COALESCE(SUM(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN total_price END),0), " +
  "COALESCE(AVG(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN total_price END),0) " +
  "FROM bookings WHERE user_id = :userId", nativeQuery = true)
Object[] getUserBookingSummary(@Param("userId") Long userId);

// User active count
@Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId " +
  "AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS','COMPLETING','PAYMENT_PENDING')", nativeQuery = true)
int countActiveByUserId(@Param("userId") Long userId);

// User completed count
@Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId " +
  "AND status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED')", nativeQuery = true)
long countCompletedByUserId(@Param("userId") Long userId);

// Provider summary with optional date range — status = PAID
@Query(value = "SELECT COUNT(*), COALESCE(SUM(total_price),0), COALESCE(AVG(total_price),0) " +
  "FROM bookings WHERE provider_id = :providerId AND status = 'PAID' " +
  "AND (:startDate IS NULL OR appointment_date >= CAST(:startDate AS date)) " +
  "AND (:endDate IS NULL OR appointment_date <= CAST(:endDate AS date))", nativeQuery = true)
Object[] getProviderBookingSummary(@Param("providerId") Long providerId,
  @Param("startDate") String startDate, @Param("endDate") String endDate);

// Provider active count
@Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
  "AND status IN ('CONFIRMED','IN_PROGRESS','COMPLETING','PAYMENT_PENDING')", nativeQuery = true)
int countActiveByProviderId(@Param("providerId") Long providerId);

// Provider completed count
@Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
  "AND status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED')", nativeQuery = true)
long countCompletedByProviderId(@Param("providerId") Long providerId);
```

**Service layer:** Add 6 public methods in `BookingService.java` that call the repository and build DTOs. Use `BookingSummaryDTO` and `ProviderBookingSummaryDTO` from the contracts module.

> [!IMPORTANT]
> The status sets are defined by the spec:
> - **Active**: REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETING, PAYMENT_PENDING
> - **Completed** (user): COMPLETING, PAYMENT_PENDING, PAID, REFUNDED
> - **Provider summary**: only PAID bookings
> - **Provider active**: CONFIRMED, IN_PROGRESS, COMPLETING, PAYMENT_PENDING

### A2. Refactor S3-F2 — Assign Provider

**File:** `BookingService.assignProvider()` (lines 147–164)

**Current code problems:**
- Calls `bookingRepository.existsProviderById(providerId)` — this is a cross-DB query (`SELECT COUNT(*) > 0 FROM providers`)
- Does NOT publish `booking.placed` (currently published in `createBooking` instead — wrong place)

**Changes:**
1. Inject `ProviderServiceClient` (from contracts) into `BookingService` constructor
2. Replace `existsProviderById` with Feign call:
   ```java
   ProviderDTO provider;
   try {
       provider = providerServiceClient.getProvider(providerId);
   } catch (FeignException.NotFound e) {
       throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
   } catch (FeignException e) {
       throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider service unavailable");
   }
   if (!"AVAILABLE".equals(provider.status())) {
       throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is not available");
   }
   ```
3. Remove the `booking.placed` publish from `createBooking()` (line 92-93) — it should NOT fire on create
4. Add `booking.placed` publish in `assignProvider()` after save (using `publishAfterCommit`)
5. **Do NOT** directly update the provider's status to BUSY — the `booking.placed` event consumer in provider-service handles that

### A3. Refactor S3-F7 — Cancel Booking

**File:** `BookingService.cancelBooking()` (lines 167–192)

**Current code problem:** Line 177 calls `bookingRepository.updateProviderStatusToAvailable(...)` — direct cross-DB write.

**Changes:**
1. **Remove** lines 176-178 (`if (booking.getProviderId() != null) { bookingRepository.updateProviderStatusToAvailable(...) }`)
2. The `booking.cancelled` event publish (already at lines 189-190) will trigger provider-service to set the provider back to AVAILABLE via its own consumer
3. Verify the cancel event payload carries `reason: "user_requested"` (current code uses `"cancelled by user"` — update to match spec)
4. Verify the payload still includes `providerId` even when it's null (provider-service silently ignores null `providerId`)

### A4. Refactor S3-F10 — Analytics Dashboard

**File:** `BookingService.computeDashboardAnalytics()` (lines 386–418)

**Current code problem:** `getDashboardAggregates` does `LEFT JOIN invoices` — cross-DB query.

**Changes:**
1. Inject `InvoiceServiceClient` into `BookingService` constructor
2. Replace `getDashboardAggregates` with a two-step approach:
   - Step 1: Local query for `totalBookings`, `bookingsByStatus` (reuse existing `getDashboardStatusBreakdown`)
   - Step 2: Collect booking IDs with completed statuses (`COMPLETING, PAYMENT_PENDING, PAID, REFUNDED`)
   - Step 3: Feign batch call `invoiceServiceClient.getInvoiceAmountsByBookings(new InvoiceAmountsRequest(bookingIds))`
   - Step 4: Sum returned amounts for `totalRevenue`
   - Step 5: Compute `averageBookingValue = totalRevenue / count(bookings with a COMPLETED invoice in the map)`. If denominator is 0 → return 0. **Do NOT** divide by totalBookings — divide by the count of bookingIds that actually have a COMPLETED invoice entry in the returned map.
3. Add new repository query to get booking IDs by status and date range:
   ```java
   @Query("SELECT b.id FROM Booking b WHERE b.status IN :statuses " +
     "AND b.requestedAt >= :start AND b.requestedAt <= :end")
   List<Long> findIdsByStatusInAndDateRange(...);
   ```
4. Update `completionRate` calculation: completed set = `COMPLETING, PAYMENT_PENDING, PAID, REFUNDED` divided by `totalBookings`
5. Remove the old `getDashboardAggregates` repository method (or keep but unused)
6. **Preserve MongoDB logging**: the current `logAnalyticsViewed()` call must remain — it logs `ANALYTICS_VIEWED` to MongoDB on every invocation (even cache hits). Do not move it inside the cache-miss block.
7. **Preserve cache**: 10-minute TTL via programmatic cache (current pattern). Do not switch to `@Cacheable`.
8. Handle empty bookingIds list: if no completed bookings exist, skip the Feign call entirely, set `totalRevenue=0`, `averageBookingValue=0`

---

## Agent B: Detailed Tasks

### B1. Refactor S3-F4 — Complete Booking (Saga Trigger)

**File:** `BookingService.completeBooking()` (lines 270–303)

**Current code:** Sets status to COMPLETING, computes totalPrice, publishes `booking.completed`. **Missing:** the 3 pre-saga Feign checks and ADMIN auth gate.

**Changes:**
1. Inject `UserServiceClient`, `ProviderServiceClient`, `CalendarServiceClient` into constructor (Agent A injects ProviderServiceClient; Agent B adds the other two — coordinate to avoid conflict, or one agent injects all three)
2. Add **ADMIN-only auth check** at the very top of the method (before `findById`) — see Auth Enforcement section below
3. After status validation (line 274-277), **compute totalPrice first** (lines 282-289 stay where they are — price must be calculated before the Feign checks so it can be included in the event payload)
4. After totalPrice calculation, add 3 Feign pre-checks:

```java
// Pre-check 1: User must be ACTIVE
UserDTO user;
try {
    user = userServiceClient.getUser(booking.getUserId());
} catch (FeignException.NotFound e) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found");
}
if (!"ACTIVE".equals(user.status())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not active");
}

// Pre-check 2: Provider must be BUSY
ProviderDTO provider;
try {
    provider = providerServiceClient.getProvider(booking.getProviderId());
} catch (FeignException.NotFound e) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider not found");
}
if (!"BUSY".equals(provider.status())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is not BUSY");
}

// Pre-check 3: Calendar slot must exist
try {
    calendarServiceClient.getSlotForBooking(
        booking.getProviderId(),
        booking.getAppointmentDate().toString(),
        booking.getStartTime().toString());
} catch (FeignException.NotFound e) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No calendar slot found for this booking");
}
```

5. **Execution order inside the method must be:**
   1. ADMIN auth check (403 if not)
   2. Find booking (404 if not found)
   3. Status == IN_PROGRESS check (400 if not)
   4. Calculate totalPrice if null (local, from BookingItem prices)
   5. Three Feign pre-checks (400 if any fail — NO event published, NO status change)
   6. Set status = COMPLETING, completedAt = now(), save
   7. Publish `booking.completed` event after commit
   8. Return 200

> [!WARNING]
> The totalPrice calc (step 4) must happen BEFORE the Feign checks (step 5) because if any check fails, we abort cleanly. But the status change (step 6) must happen AFTER all checks pass. The current code already has the right structure — just insert steps 2 and 5 into the existing flow.

### B2. Refactor S3-F11 — Record Interaction

**File:** `BookingService.recordInteraction()` (lines 560–611)

**Current code problems:**
- Line 563: checks `status != COMPLETED` — needs to also accept saga statuses
- Neo4j graph uses raw provider/user IDs but does NOT fetch name/specialty via Feign (the M2 code used direct SQL; in M3 neither table exists in booking-postgres)

**Changes:**
1. Update status check to accept saga-completed statuses:
   ```java
   Set<Booking.Status> allowed = Set.of(
       Booking.Status.COMPLETED, Booking.Status.COMPLETING,
       Booking.Status.PAYMENT_PENDING, Booking.Status.PAID);
   if (!allowed.contains(booking.getStatus())) {
       throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be completed to record interaction");
   }
   ```
2. Add Feign calls to fetch user name and provider name/specialty **with error handling**:
   ```java
   UserDTO user;
   try {
       user = userServiceClient.getUser(booking.getUserId());
   } catch (FeignException e) {
       throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
   }
   ProviderDTO provider;
   try {
       provider = providerServiceClient.getProvider(booking.getProviderId());
   } catch (FeignException e) {
       throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider service unavailable");
   }
   ```
3. Update the Neo4j MERGE query to set `name` on UserNode and `name`, `specialty` on ProviderNode:
   ```java
   neo4jClient.query(
       "MERGE (u:User {id: $userId}) SET u.name = $userName " +
       "MERGE (p:Provider {id: $providerId}) SET p.name = $providerName, p.specialty = $specialty " +
       "MERGE (u)-[r:BOOKED]->(p) ...")
       .bind(user.name()).to("userName")
       .bind(provider.name()).to("providerName")
       .bind(provider.specialty()).to("specialty")
       // ... existing bindings
   ```

### B3. Refactor S3-F12 — Recommendations

**File:** `BookingService.getRecommendations()` (lines 479–553)

**Current code problems:**
- Line 501: `bookingRepository.existsUserById(userId)` — cross-DB query (`SELECT COUNT(*) > 0 FROM users`)
- Line 529: `bookingRepository.findProvidersByIds(providerIds)` — cross-DB query (`SELECT id, name, specialty FROM providers`)

**Changes:**
1. Replace user existence check with Feign:
   ```java
   try {
       userServiceClient.getUser(userId);
   } catch (FeignException.NotFound e) {
       throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
   }
   ```
2. Replace `findProvidersByIds` with per-provider Feign calls:
   ```java
   Map<Long, ProviderDTO> providerMap = new HashMap<>();
   for (Long pid : providerIds) {
       try {
            providerMap.put(pid, providerServiceClient.getProvider(pid));
        } catch (FeignException e) {
            // skip unavailable providers
        }
    }
    ```
3. Update the DTO assembly to use `ProviderDTO` fields:
   ```java
   ProviderDTO pDto = providerMap.get(pid);
   String name = pDto != null ? pDto.name() : "";
   String specialty = pDto != null ? pDto.specialty() : "";
   ```
4. **Preserve cache**: the existing `@Cacheable(cacheNames = "booking-service::S3-F12")` annotation must remain unchanged.
5. **Preserve auth check**: the existing ownership check (caller == userId or ADMIN) must remain unchanged.

### B4. Remove Cross-Service Repository Queries

**File:** `BookingRepository.java`

Remove these methods that query tables no longer in booking-postgres:
- `existsUserById` (line 19-20) — queries `users` table
- `existsProviderById` (line 23-24) — queries `providers` table
- `findProvidersByIds` (line 27-28) — queries `providers` table
- `updateProviderStatusToAvailable` (line 35-37) — updates `providers` table
- `createInvoiceForBooking` (line 42-47) — inserts into `invoices` table

Also update `getDashboardAggregates` (line 68-76) to remove the `LEFT JOIN invoices` — Agent A handles this as part of S3-F10 refactor. Coordinate: Agent B deletes the method, Agent A adds the replacement.

> [!WARNING]
> After removing `existsProviderById`, the `getEstimate()` method (line 242) also calls it. Per the spec, S3-F3 has **no M3 change** — the `bookings` table query is fine, but the `providers` existence check IS cross-service. Since the spec says "No Feign call is needed" for S3-F3, simply **remove the provider existence check** from `getEstimate()` (lines 241-245). The demand multiplier query on `bookings` table stays.

---

## Constructor Injection Coordination

Both agents modify `BookingService` constructor. To avoid merge conflicts:

**Agent A adds:** `ProviderServiceClient`, `InvoiceServiceClient`
**Agent B adds:** `UserServiceClient`, `CalendarServiceClient`

Both must update the constructor parameter list and field declarations. **Recommendation:** Agent A goes first, Agent B rebases.

---

## Review Plan

### Review Checklist for Agent A

| # | Check | What to verify |
|---|-------|---------------|
| 1 | **Endpoints return correct types** | `/user/{userId}/summary` → `BookingSummaryDTO`, `/provider/{providerId}/summary` → `ProviderBookingSummaryDTO`. Return types match contracts module exactly. |
| 2 | **Status sets match spec** | Active = `REQUESTED,CONFIRMED,IN_PROGRESS,COMPLETING,PAYMENT_PENDING`. User-completed = `COMPLETING,PAYMENT_PENDING,PAID,REFUNDED`. Provider summary = `PAID` only. |
| 3 | **Provider summary date filtering** | `startDate`/`endDate` are optional query params; when absent, return all-time. |
| 4 | **S3-F2 Feign error handling** | `FeignException.NotFound` → 404. Other `FeignException` → 503. Provider status != AVAILABLE → 400. |
| 5 | **S3-F2 event moved** | `booking.placed` removed from `createBooking()`, added to `assignProvider()` inside `publishAfterCommit`. |
| 6 | **S3-F7 no direct provider update** | `updateProviderStatusToAvailable()` call removed. Event `booking.cancelled` is published (already present). |
| 7 | **S3-F10 batch Feign** | Single `POST /api/invoices/by-bookings` call, NOT per-booking Feign. Empty bookingIds → skip Feign, totalRevenue=0. |
| 8 | **S3-F10 completionRate** | Uses saga-completed set (`COMPLETING,PAYMENT_PENDING,PAID,REFUNDED`) / totalBookings. |
| 9 | **No cross-DB queries remain** | No `JOIN invoices`, no reference to `providers` or `users` tables in any Agent A code. |
| 10 | **Cache invalidation preserved** | All existing `cacheInvalidator` calls in modified methods are kept. |
| 11 | **Compile check** | `mvn compile -pl booking-service` passes. |

### Review Checklist for Agent B

| # | Check | What to verify |
|---|-------|---------------|
| 1 | **S3-F4 three pre-checks** | User ACTIVE, Provider BUSY, Calendar slot exists — all three checked before any state change. |
| 2 | **S3-F4 check order** | Feign checks happen AFTER `status == IN_PROGRESS` validation but BEFORE `setStatus(COMPLETING)`. |
| 3 | **S3-F4 error on Feign failure** | Any pre-check failure → 400 (not 500). No event published. Booking stays IN_PROGRESS. |
| 4 | **S3-F11 status set** | Accepts `COMPLETED, COMPLETING, PAYMENT_PENDING, PAID` — NOT `CANCELLED`, `REFUNDED`, `PAYMENT_FAILED`. |
| 5 | **S3-F11 Feign enrichment** | Neo4j nodes get `name` (user), `name`+`specialty` (provider) from Feign DTOs. |
| 6 | **S3-F11 idempotency preserved** | The `recorded_booking_ids` check still works. No regression. |
| 7 | **S3-F12 user check via Feign** | `existsUserById` replaced with `userServiceClient.getUser()`. FeignException.NotFound → 404. |
| 8 | **S3-F12 provider enrichment via Feign** | `findProvidersByIds` replaced with per-provider Feign calls. Missing provider → skip gracefully. |
| 9 | **Repository cleanup complete** | `existsUserById`, `existsProviderById`, `findProvidersByIds`, `updateProviderStatusToAvailable`, `createInvoiceForBooking` all removed. |
| 10 | **S3-F3 provider check removed** | `getEstimate()` no longer calls `existsProviderById`. Demand multiplier query on `bookings` stays. |
| 11 | **No cross-DB queries remain** | No reference to `users`, `providers`, or `invoices` tables in any Agent B code. |
| 12 | **Compile check** | `mvn compile -pl booking-service` passes. |

### Integration Review (after both agents merge)

| # | Check |
|---|-------|
| 1 | Constructor has all 4 new Feign clients injected: `ProviderServiceClient`, `InvoiceServiceClient`, `UserServiceClient`, `CalendarServiceClient` |
| 2 | `BookingRepository` has zero native queries referencing `users`, `providers`, or `invoices` tables |
| 3 | All 6 new endpoints respond with correct DTOs (manual curl or test) |
| 4 | `createBooking()` does NOT publish `booking.placed` |
| 5 | `assignProvider()` publishes `booking.placed` |
| 6 | `completeBooking()` runs 3 Feign pre-checks then publishes `booking.completed` |
| 7 | `cancelBooking()` does NOT write to `providers` table; publishes `booking.cancelled` |
| 8 | `recordInteraction()` uses Feign for user/provider data |
| 9 | `getRecommendations()` uses Feign for user existence + provider enrichment |
| 10 | `computeDashboardAnalytics()` uses batch Feign to invoice-service |
| 11 | `mvn clean compile -pl booking-service` passes |
| 12 | All existing cache annotations and invalidation calls are preserved |

---

## File Change Summary

| File | Agent | Changes |
|------|-------|---------|
| `BookingController.java` | A | +6 endpoint methods |
| `BookingService.java` | A+B | +6 endpoint service methods, refactor assignProvider/cancelBooking/completeBooking/recordInteraction/getRecommendations/computeDashboardAnalytics, inject 4 Feign clients, remove `booking.placed` from createBooking |
| `BookingRepository.java` | A+B | +query methods for new endpoints, remove 5 cross-DB methods, remove/replace getDashboardAggregates |
| `getEstimate()` in BookingService | B | Remove provider existence check (lines 241-245) |
| `FeignCorrelationConfig.java` | — | Already exists (verified). Forwards `X-Correlation-ID` on outgoing Feign calls. No changes needed. |
| `BookingEventPublisher.java` | — | Already exists (verified). Publishes placed/completed/cancelled. No changes needed. |
| `BookingPaymentEventListener.java` | — | Already exists (verified). Consumers for payment.initiated/completed/failed/refunded. No changes needed. |
| `BookingEventConfig.java` | — | Already exists (verified). Exchange, queue, DLQ, bindings. No changes needed. |
| `application.yml` | — | Already has all feign URLs and RabbitMQ config. No changes needed. |

---

## Additional Required Details

### Auth Enforcement (Agent B owns S3-F4 and S3-F7)

**S3-F4 `completeBooking` — ADMIN only:**
The spec says: *"Reject with 403 if the caller's X-User-Role is not ADMIN."*
Add at the top of `completeBooking()`:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
boolean isAdmin = auth != null && auth.getAuthorities().stream()
    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
if (!isAdmin) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can complete bookings");
}
```

**S3-F7 `cancelBooking` — Owner or ADMIN:**
The spec says: *"The caller must be the booking's owner (booking.userId == X-User-Id from JWT) or ADMIN. Reject with 403 if neither."*
Add after finding the booking:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
boolean isAdmin = auth != null && auth.getAuthorities().stream()
    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
if (!isAdmin) {
    Object principal = auth.getPrincipal();
    Long callerUid = null;
    if (principal instanceof Map<?, ?> map) {
        Object idVal = map.get("id");
        if (idVal != null) callerUid = ((Number) idVal).longValue();
    }
    if (callerUid == null || !callerUid.equals(booking.getUserId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own bookings");
    }
}
```

> [!IMPORTANT]
> The saga compensation path (`payment.failed` → `booking.cancelled`) runs inside `BookingPaymentEventListener`, NOT through the HTTP endpoint. It bypasses auth entirely — this is correct and intentional. No changes needed there.

### Verify `GET /api/bookings/{bookingId}` Response Shape

The spec requires this endpoint (already M1 CRUD) to return: `id, userId, providerId, status, totalPrice, appointmentDate, startTime, endTime, completedAt, metadata`.

The current `getBookingById()` returns the full `Booking` entity which includes all these fields. **No code change needed**, but add to the integration review checklist:
- Verify the JSON response includes all 10 fields
- Verify `metadata` (JSONB) serializes correctly

### `logback-spring.xml` with Loki4J Appender (Agent A)

This is an S3 deliverable. Create `booking-service/src/main/resources/logback-spring.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>http://${LOKI_HOST:-loki}:3100/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <pattern>app=booking-service,host=${HOSTNAME},level=%level</pattern>
            </label>
            <message>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </message>
        </format>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
```
Also add the Loki4J dependency to `booking-service/pom.xml` if not already present:
```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.5.2</version>
</dependency>
```

### Updated Review Checklist Additions

Add to **Agent A review**:
| 12 | **logback-spring.xml exists** | File created in `src/main/resources/`, Loki4J appender configured, dependency in pom.xml |
| 13 | **S3-F10 MongoDB log preserved** | `logAnalyticsViewed()` still fires on every call including cache hits |
| 14 | **S3-F10 averageBookingValue denominator** | Divided by count of bookings with COMPLETED invoice (from Feign map size), NOT by totalBookings |
| 15 | **S3-F7 cancel reason string** | Event payload uses `reason: "user_requested"` (not `"cancelled by user"`) |

Add to **Agent B review**:
| 13 | **S3-F4 ADMIN auth** | Non-ADMIN caller gets 403. ADMIN caller proceeds. |
| 14 | **S3-F7 owner-or-ADMIN auth** | Owner can cancel. ADMIN can cancel. Other user gets 403. |
| 15 | **Saga compensation bypasses auth** | `BookingPaymentEventListener.handlePaymentFailed` publishes `booking.cancelled` without HTTP auth — correct. |
| 16 | **S3-F4 execution order** | Auth → findById → status check → totalPrice calc → 3 Feign checks → status=COMPLETING → publish. Never publish if any check fails. |
| 17 | **S3-F11 Feign error handling** | Both user-service and provider-service Feign calls wrapped in try-catch |
| 18 | **S3-F12 cache preserved** | `@Cacheable("booking-service::S3-F12")` annotation unchanged |

Add to **Integration review**:
| 13 | `GET /api/bookings/{id}` returns all 10 required fields in JSON |
| 14 | `PUT /api/bookings/{id}/complete` returns 403 for non-ADMIN |
| 15 | `PUT /api/bookings/{id}/cancel` returns 403 for non-owner non-ADMIN |
| 16 | `FeignCorrelationConfig` still present — `X-Correlation-ID` forwarded on all outgoing Feign calls |
| 17 | No `import` references to `providers`, `users`, or `invoices` entity classes in booking-service code |
| 18 | Observer pattern preserved — `MongoEventLogger` still registered, `emitAfterCommit` still called in all write paths |
