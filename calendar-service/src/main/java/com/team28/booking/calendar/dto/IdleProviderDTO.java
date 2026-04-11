package com.team28.booking.calendar.dto;

public record IdleProviderDTO(
        Long providerId,
        String providerName,
        String specialty,
        Double rating,
        Long bookedSlotsCount,
        Long totalSlotsCount
) {
}
