package com.team28.booking.contracts.events;

public record SlotReservedEvent(Long slotId, Long providerId, Long bookingId) {
}
