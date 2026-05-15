package com.team28.booking.contracts.dto;

import java.math.BigDecimal;

public record BookingSummaryDTO(
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        BigDecimal totalSpent,
        BigDecimal averageBookingPrice
) {
}
