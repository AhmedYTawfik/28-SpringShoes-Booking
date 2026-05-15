package com.team28.booking.contracts.events;

public record BookingCompletedEvent(Long bookingId, Long userId, Long providerId, Double totalPrice) {
}
