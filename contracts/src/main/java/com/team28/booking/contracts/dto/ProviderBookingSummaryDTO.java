package com.team28.booking.contracts.dto;

import java.math.BigDecimal;

public record ProviderBookingSummaryDTO(
        long totalBookings,
        BigDecimal totalEarnings,
        BigDecimal averageBookingPrice
) {
}
