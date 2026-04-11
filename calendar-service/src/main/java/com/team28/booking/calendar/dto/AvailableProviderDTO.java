package com.team28.booking.calendar.dto;

public record AvailableProviderDTO(
        Long providerId,
        String providerName,
        String specialty,
        Double rating,
        Long availableSlots
) {
}
