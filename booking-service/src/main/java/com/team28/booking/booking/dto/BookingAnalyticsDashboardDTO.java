package com.team28.booking.booking.dto;

import java.math.BigDecimal;
import java.util.Map;

public record BookingAnalyticsDashboardDTO(
        long totalBookings,
        BigDecimal totalRevenue,
        BigDecimal averageBookingValue,
        double completionRate,
        Map<String, Long> bookingsByStatus
) {
    public static BookingAnalyticsDashboardDTOBuilder builder() {
        return new BookingAnalyticsDashboardDTOBuilder();
    }
}