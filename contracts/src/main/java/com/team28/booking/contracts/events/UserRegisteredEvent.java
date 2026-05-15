package com.team28.booking.contracts.events;

public record UserRegisteredEvent(Long userId, String email, String role) {
}
