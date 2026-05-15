package com.team28.booking.contracts.dto;

import java.time.LocalDateTime;

public record ProviderAvailabilityDTO(
        Long providerId,
        String status,
        boolean available,
        int activeBookingCount,
        LocalDateTime nextAvailableAt
) {
}
