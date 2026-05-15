package com.team28.booking.contracts.events;

public record PaymentCompletedEvent(Long invoiceId, Long bookingId, Double amount) {
}
