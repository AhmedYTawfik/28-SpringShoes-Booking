package com.team28.booking.calendar.controller;

import com.team28.booking.calendar.dto.AvailabilitySnapshotRequest;
import com.team28.booking.calendar.dto.CalendarAnalyticsDTO;
import com.team28.booking.calendar.service.AvailabilityHistoryService;
import com.team28.booking.calendar.service.CalendarAnalyticsService;
import com.team28.booking.calendar.service.TimeSlotService;
import com.team28.booking.calendar.dto.AvailabilitySnapshotDTO;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * S4-F10 (Phase 1): Calendar analytics endpoint.
 *
 * <p>Caching design: the data fetch goes through {@link CalendarAnalyticsService#getCachedAnalytics}
 * which is annotated {@code @Cacheable}. The ANALYTICS_VIEWED Observer event is fired here,
 * OUTSIDE the cached call, so it executes on every request — even on cache hits.</p>
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarAnalyticsService calendarAnalyticsService;
    private final TimeSlotService timeSlotService;
    private final AvailabilityHistoryService availabilityHistoryService;

    public CalendarController(CalendarAnalyticsService calendarAnalyticsService,
                              TimeSlotService timeSlotService,
                              AvailabilityHistoryService availabilityHistoryService) {
        this.calendarAnalyticsService = calendarAnalyticsService;
        this.timeSlotService = timeSlotService;
        this.availabilityHistoryService = availabilityHistoryService;
    }

    /**
     * GET /api/calendar/analytics?startDate=&endDate=
     * S4-F10: Get Calendar Analytics Dashboard.
     */
    @GetMapping("/analytics")
    public CalendarAnalyticsDTO getAnalytics(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        // Data fetch (potentially served from Redis cache)
        CalendarAnalyticsDTO dto = calendarAnalyticsService.getCachedAnalytics(startDate, endDate);
        // Log ANALYTICS_VIEWED unconditionally — runs on every call including cache hits
        logAnalyticsViewed(startDate, endDate);
        return dto;
    }

    /**
     * POST /api/calendar/{providerId}/availability-snapshot
     * S4-F11: Record Provider Availability Snapshot.
     * Returns 201 Created on success.
     */
    @PostMapping("/{providerId}/availability-snapshot")
    @ResponseStatus(HttpStatus.CREATED)
    public void recordSnapshot(
            @PathVariable Long providerId,
            @Valid @RequestBody AvailabilitySnapshotRequest request) {
        timeSlotService.recordAvailabilitySnapshot(providerId, request);
    }

    /**
     * GET /api/calendar/{providerId}/availability-history
     * S4-F12: Get Provider Availability History.
     */
    @GetMapping("/{providerId}/availability-history")
    public List<AvailabilitySnapshotDTO> getAvailabilityHistory(
            @PathVariable Long providerId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Instant start = parseToInstant(startTime);
        Instant end = parseToInstant(endTime);

        if (start != null && end != null && start.isAfter(end)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startTime must be before or equal to endTime");
        }
        return availabilityHistoryService.getAvailabilityHistory(providerId, start, end);
    }

    private Instant parseToInstant(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(timeString);
        } catch (java.time.format.DateTimeParseException e) {
            return LocalDateTime.parse(timeString).atZone(java.time.ZoneId.systemDefault()).toInstant();
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void logAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("startDate", startDate.toString());
        payload.put("endDate", endDate.toString());
        payload.put("action", "ANALYTICS_VIEWED");
        timeSlotService.fireEvent("ANALYTICS_VIEWED", payload);
    }
}
