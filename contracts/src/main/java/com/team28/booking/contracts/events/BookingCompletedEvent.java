package com.team28.booking.contracts.events;

import java.math.BigDecimal;

public record BookingCompletedEvent(Long bookingId, Long userId, Long providerId, BigDecimal totalPrice) {
}
