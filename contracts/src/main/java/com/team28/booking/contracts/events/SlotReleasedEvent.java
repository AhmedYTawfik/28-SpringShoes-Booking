package com.team28.booking.contracts.events;

public record SlotReleasedEvent(Long slotId, Long providerId, Long bookingId) {
}
