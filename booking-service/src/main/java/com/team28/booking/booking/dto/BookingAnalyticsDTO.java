package com.team28.booking.booking.dto;

public record BookingAnalyticsDTO(
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        double totalRevenue,
        double averageBookingPrice,
        double completionRate
) {
}
