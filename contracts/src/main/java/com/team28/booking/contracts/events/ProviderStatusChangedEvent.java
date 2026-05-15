package com.team28.booking.contracts.events;

public record ProviderStatusChangedEvent(Long providerId, String oldStatus, String newStatus) {
}
