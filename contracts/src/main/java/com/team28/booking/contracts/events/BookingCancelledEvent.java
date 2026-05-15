package com.team28.booking.contracts.events;

public record BookingCancelledEvent(Long bookingId, Long userId, Long providerId, String reason) {
}
