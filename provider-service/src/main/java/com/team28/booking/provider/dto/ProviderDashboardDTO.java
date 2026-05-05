package com.team28.booking.provider.dto;

public record ProviderDashboardDTO(
    Long providerId,
    String name,
    Long totalBookings,
    Double totalRevenue,
    Double averageBookingValue,
    Double averageRating,
    Double utilizationRate
) {
    public static ProviderDashboardDTOBuilder builder() {
        return new ProviderDashboardDTOBuilder();
    }
}