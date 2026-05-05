package com.team28.booking.booking.dto;

import java.math.BigDecimal;

public record BookingAnalyticsDTO(
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        BigDecimal totalRevenue,
        BigDecimal averageBookingPrice,
        double completionRate
) {
    public static BookingAnalyticsDTOBuilder builder() {
        return new BookingAnalyticsDTOBuilder();
    }
}