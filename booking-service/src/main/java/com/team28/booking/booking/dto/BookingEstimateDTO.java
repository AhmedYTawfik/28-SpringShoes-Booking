package com.team28.booking.booking.dto;

import java.math.BigDecimal;

public record BookingEstimateDTO(
        int totalDuration,
        BigDecimal basePrice,
        BigDecimal estimatedPrice,
        BigDecimal demandMultiplier
) {
}
