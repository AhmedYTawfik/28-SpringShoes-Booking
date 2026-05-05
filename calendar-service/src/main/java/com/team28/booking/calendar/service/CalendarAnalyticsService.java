package com.team28.booking.calendar.service;

import com.team28.booking.calendar.dto.CalendarAnalyticsDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * S4-F10: Thin caching wrapper for calendar analytics.
 *
 * <p>The @Cacheable annotation is placed here (not on TimeSlotService or the controller)
 * to solve the Spring self-invocation proxy issue: when the controller calls this
 * separate bean, the call goes through the Spring proxy and caching works correctly.</p>
 *
 * <p>The ANALYTICS_VIEWED Observer event is fired by the controller OUTSIDE this
 * cached call so it runs on every request, including cache hits.</p>
 */
@Service
public class CalendarAnalyticsService {

    private final TimeSlotService timeSlotService;

    public CalendarAnalyticsService(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    /**
     * Returns analytics for the given date range.
     * Cache name: {@code calendar-service::S4-F10} (10-min TTL, configured in CacheConfig).
     * Key is a hash of the two dates to produce a single Redis key.
     */
    @Cacheable(cacheNames = "calendar-service::S4-F10",
               key = "T(java.util.Objects).hash(#startDate, #endDate)")
    public CalendarAnalyticsDTO getCachedAnalytics(LocalDate startDate, LocalDate endDate) {
        return timeSlotService.getCalendarAnalytics(startDate, endDate);
    }
}
