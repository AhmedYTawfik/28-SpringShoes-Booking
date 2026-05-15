package com.team28.booking.contracts.events;

public record ProviderRatedEvent(Long providerId, Long bookingId, Double rating, Long userId) {
}
