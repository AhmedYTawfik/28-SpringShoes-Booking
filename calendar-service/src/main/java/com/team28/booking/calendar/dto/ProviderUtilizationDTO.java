package com.team28.booking.calendar.dto;

public record ProviderUtilizationDTO(
        Long providerId,
        Long totalSlots,
        Long bookedSlots,
        Long availableSlots,
        Double utilizationRate,
        String peakDay
) {
    public static ProviderUtilizationDTOBuilder builder() { return new ProviderUtilizationDTOBuilder(); }
}
