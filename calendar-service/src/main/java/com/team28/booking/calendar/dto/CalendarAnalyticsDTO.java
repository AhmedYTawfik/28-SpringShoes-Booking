package com.team28.booking.calendar.dto;

import java.util.Map;

/**
 * S4-F10: Calendar Analytics Dashboard DTO.
 * Builder pattern (DP-4) — external builder class CalendarAnalyticsDTOBuilder.
 */
public record CalendarAnalyticsDTO(
        long totalSlots,
        long availableSlots,
        long bookedSlots,
        double utilizationRate,
        Map<String, Long> slotsByDate
) {
    public static CalendarAnalyticsDTOBuilder builder() {
        return new CalendarAnalyticsDTOBuilder();
    }
}
