package com.team28.booking.contracts.events;

public record BookingPlacedEvent(Long bookingId, Long userId, Long providerId) {
}
