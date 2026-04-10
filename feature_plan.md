# S4-F8: Provider Utilization Summary (DTO) — Implementation Plan

## Overview

Add a `GET /api/timeslots/provider/{providerId}/utilization?startDate={d}&endDate={d}` endpoint  
to the **calendar-service** that returns a `ProviderUtilizationDTO` with aggregate slot counts,  
utilization rate, and the peak booking day for a given provider and date range.

**Branch**: `feat/calendar/S4-F8/<ID>`  
**Service port**: `8080` (calendar-service, already running)  
**Provider-service**: must be started separately on port `8085` for testing

---

## Current State (What Already Exists)

| Layer | File | Status |
|---|---|---|
| Entity | `model/TimeSlot.java` | ✅ Exists |
| Repository | `repository/TimeSlotRepository.java` | ✅ Exists — needs 2 new query methods |
| Service | `service/TimeSlotService.java` | ✅ Exists — needs 1 new method |
| Controller | `controller/TimeSlotController.java` | ✅ Exists — needs 1 new endpoint |
| DTO | `dto/ProviderUtilizationDTO.java` | ❌ Must be created |

---

## Proposed Changes

### DTO Layer

#### [NEW] `ProviderUtilizationDTO.java`

```
calendar-service/src/main/java/com/team28/booking/calendar/dto/ProviderUtilizationDTO.java
```

Fields: `providerId` (Long), `totalSlots` (Long), `bookedSlots` (Long),  
`availableSlots` (Long), `utilizationRate` (Double), `peakDay` (String).

Include: default constructor, all-args style setters and getters.

---

### Repository Layer

#### [MODIFY] `TimeSlotRepository.java`

Add **2** new native SQL query methods:

**1. Aggregate counts query** — single row with `totalSlots`, `bookedSlots`, `availableSlots`:
```sql
SELECT
    COUNT(*) AS totalSlots,
    COUNT(*) FILTER (WHERE available = false) AS bookedSlots,
    COUNT(*) FILTER (WHERE available = true) AS availableSlots
FROM time_slots
WHERE provider_id = :providerId
  AND date >= :startDate AND date <= :endDate
```
Return type: `Object[]`

**2. Peak day query** — day-of-week string with the most booked slots:
```sql
SELECT TO_CHAR(date, 'Day') AS dayOfWeek
FROM time_slots
WHERE provider_id = :providerId
  AND date >= :startDate AND date <= :endDate
  AND available = false
GROUP BY TO_CHAR(date, 'Day')
ORDER BY COUNT(*) DESC
LIMIT 1
```
Return type: `String`

> [!WARNING]
> `TO_CHAR(date, 'Day')` in PostgreSQL returns a **fixed-width, space-padded** string (e.g., `"Monday   "`). The service layer must call `.trim()` on the result before putting it in the DTO.

---

### Service Layer

#### [MODIFY] `TimeSlotService.java`

Add `getUtilization(Long providerId, LocalDate startDate, LocalDate endDate)`:

1. `countProviderById(providerId)` → if 0, throw `ResponseStatusException(NOT_FOUND, "Provider not found")`
2. `getUtilizationStats(...)` → cast `stats` as `Object[]` (single aggregate row)
3. Cast each cell to `Number`, extract `.longValue()` for counts
4. `utilizationRate = totalSlots > 0 ? (double) bookedSlots / totalSlots * 100.0 : 0.0`
5. `findPeakDay(...)` → trim the result if not null
6. Build and return `ProviderUtilizationDTO`

---

### Controller Layer

#### [MODIFY] `TimeSlotController.java`

Add:
```java
@GetMapping("/provider/{providerId}/utilization")
public ProviderUtilizationDTO getUtilization(
        @PathVariable Long providerId,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate) {
    return timeSlotService.getUtilization(providerId, startDate, endDate);
}
```

---

## Testing Plan

> [!IMPORTANT]
> Testing is purely **endpoint-based** using `curl`. No test files will be created or modified.

### Pre-Test Setup

1. **Verify Docker is up**: `docker ps` — the `booking-db` container must be running.
2. **Calendar-service** is already running on port `8080` (confirmed from terminal).
3. **Provider-service** must be started separately on port `8085`:
   ```
   cd provider-service && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8085"
   ```
   (Provider-service's own `application.properties` has `server.port=8080` by default, so we override.)

---

### Test Data Setup (via curl)

**Step 1**: Create 1 provider via provider-service (port 8085):
```bash
curl -s -X POST http://localhost:8085/api/providers \
  -H "Content-Type: application/json" \
  -d '{"name":"Dr. Test","email":"test@provider.com","phone":"0123456789","specialty":"Dentist","status":"AVAILABLE","rating":4.5,"totalRatings":10}'
```
Capture the `id` from the response → used as `{providerId}`.

**Step 2**: Create 20 time slots via calendar-service (port 8080).

- **15 booked** slots (`available: false`) — spread across different days in March 2026:
  - 5 on Mondays (Mar 2, 9, 16, 23, 30)
  - 5 on Wednesdays (Mar 4, 11, 18, 25) + 1 extra Monday
  - 5 on Fridays (Mar 6, 13, 20)
  → Monday ends up with the most booked slots → `peakDay = "Monday"`
  
- **5 available** slots (`available: true`):
  - Use any 5 dates in March

Each slot created via:
```bash
curl -s -X POST http://localhost:8080/api/timeslots \
  -H "Content-Type: application/json" \
  -d '{"providerId":{id},"date":"2026-03-02","startTime":"09:00","endTime":"10:00","available":false}'
```

---

### Test Suite

#### TC-1 (from spec): Happy Path — Full March Utilization
```
GET /api/timeslots/provider/{providerId}/utilization?startDate=2026-03-01&endDate=2026-03-31
```
**Expected**:
- HTTP 200
- `totalSlots = 20`
- `bookedSlots = 15`
- `availableSlots = 5`
- `utilizationRate = 75.0`
- `peakDay` = the day of week with the most booked slots (expected: `"Monday"`)

---

#### TC-2 (from spec): Non-existent Provider → 404
```
GET /api/timeslots/provider/999999/utilization?startDate=2026-03-01&endDate=2026-03-31
```
**Expected**: HTTP 404

---

#### TC-3 (extra): Provider exists but zero slots in range → 0 counts, 0.0 rate, null peakDay
```
GET /api/timeslots/provider/{providerId}/utilization?startDate=2025-01-01&endDate=2025-01-31
```
**Expected**:
- HTTP 200
- `totalSlots = 0`, `bookedSlots = 0`, `availableSlots = 0`
- `utilizationRate = 0.0`
- `peakDay = null`

---

#### TC-4 (extra): Partial date range — only some slots included
```
GET /api/timeslots/provider/{providerId}/utilization?startDate=2026-03-01&endDate=2026-03-15
```
**Expected**: HTTP 200 with counts only for slots in the first half of March  
(exact numbers depend on data created in TC-1 setup, but totals < 20)

---

#### TC-5 (extra): Provider with only available slots → utilizationRate = 0.0
- Create a second provider.
- Create 5 available (`available=true`) slots in April 2026 for that provider.
```
GET /api/timeslots/provider/{provider2Id}/utilization?startDate=2026-04-01&endDate=2026-04-30
```
**Expected**:
- `totalSlots = 5`, `bookedSlots = 0`, `availableSlots = 5`
- `utilizationRate = 0.0`
- `peakDay = null`

---

#### TC-6 (extra): Provider with only booked slots → utilizationRate = 100.0
- Create a third provider.
- Create 5 booked (`available=false`) slots for that provider, all in April 2026.
```
GET /api/timeslots/provider/{provider3Id}/utilization?startDate=2026-04-01&endDate=2026-04-30
```
**Expected**:
- `totalSlots = 5`, `bookedSlots = 5`, `availableSlots = 0`
- `utilizationRate = 100.0`

---

#### TC-7 (extra): Boundary date test — startDate equals endDate (single day)
- Use a provider that has exactly 1 booked slot on 2026-03-02.
```
GET /api/timeslots/provider/{providerId}/utilization?startDate=2026-03-02&endDate=2026-03-02
```
**Expected**:
- `totalSlots = 1`, `bookedSlots = 1`, `availableSlots = 0`
- `utilizationRate = 100.0`

---

## Verification Plan

After executing all test cases via curl, I will produce a **summary report** with:
1. Each TC, its expected vs actual HTTP status and response body
2. Pass/Fail designation
3. Any anomalies or trimmings observed (e.g. peakDay whitespace)

---

## Open Questions / Decisions

> [!IMPORTANT]
> **Zero-slot edge case for `peakDay`**: When `totalSlots = 0` (no slots in range), `findPeakDay` will return `null`. The plan handles this correctly — the DTO will have `peakDay = null`. Confirm this is acceptable.

> [!NOTE]
> The **provider-service** needs to run on a different port than the calendar-service during testing. I'll use port `8085` for provider-service. The provider record must be created there first so the `providers` table in `bookingdb` is populated (both services share the same PostgreSQL DB).
