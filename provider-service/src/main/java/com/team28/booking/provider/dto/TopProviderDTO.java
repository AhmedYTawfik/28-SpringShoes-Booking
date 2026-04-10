package com.team28.booking.provider.dto;

public record TopProviderDTO (
    Long providerId,
    String name,
    Double rating,
    Integer totalBookings
) {}
