package com.team28.booking.contracts.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ProviderDTO(
        Long id,
        Long userId,
        String name,
        String specialty,
        String status,
        Double rating,
        Integer totalRatings,
        BigDecimal basePrice,
        Map<String, Object> serviceDetails
) {
}
