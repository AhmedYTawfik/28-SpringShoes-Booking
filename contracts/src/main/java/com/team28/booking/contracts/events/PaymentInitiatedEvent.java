package com.team28.booking.contracts.events;

public record PaymentInitiatedEvent(Long invoiceId, Long bookingId, Double amount) {
}
