package com.team28.booking.contracts.events;

public record PaymentFailedEvent(Long invoiceId, Long bookingId, String reason) {
}
