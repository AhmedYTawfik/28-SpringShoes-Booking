package com.team28.booking.calendar.dto;

import java.util.Map;

/**
 * S4-F10: External Builder for CalendarAnalyticsDTO (DP-4).
 */
public class CalendarAnalyticsDTOBuilder {
    private long totalSlots;
    private long availableSlots;
    private long bookedSlots;
    private double utilizationRate;
    private Map<String, Long> slotsByDate;

    public CalendarAnalyticsDTOBuilder totalSlots(long totalSlots) { this.totalSlots = totalSlots; return this; }
    public CalendarAnalyticsDTOBuilder availableSlots(long availableSlots) { this.availableSlots = availableSlots; return this; }
    public CalendarAnalyticsDTOBuilder bookedSlots(long bookedSlots) { this.bookedSlots = bookedSlots; return this; }
    public CalendarAnalyticsDTOBuilder utilizationRate(double utilizationRate) { this.utilizationRate = utilizationRate; return this; }
    public CalendarAnalyticsDTOBuilder slotsByDate(Map<String, Long> slotsByDate) { this.slotsByDate = slotsByDate; return this; }

    public CalendarAnalyticsDTO build() {
        return new CalendarAnalyticsDTO(totalSlots, availableSlots, bookedSlots, utilizationRate, slotsByDate);
    }
}
