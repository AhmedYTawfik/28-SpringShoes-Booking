package com.team28.booking.user.dto;

import java.math.BigDecimal;

public record TopClientDTO(
        Long userId,
        String name,
        BigDecimal totalSpent,
        Long bookingCount
) {
}
