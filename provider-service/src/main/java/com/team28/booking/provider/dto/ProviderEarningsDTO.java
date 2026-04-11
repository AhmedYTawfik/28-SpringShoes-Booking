package com.team28.booking.provider.dto;

public record ProviderEarningsDTO(
        Long providerId,
        String name,
        Long totalBookings,
        Double totalEarnings,
        Double averageBookingPrice
) {}
