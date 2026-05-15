package com.team28.booking.contracts.dto;

public record ProviderUtilizationDTO(
        Long providerId,
        long totalSlots,
        long bookedSlots,
        long availableSlots,
        double utilizationRate
) {
}
