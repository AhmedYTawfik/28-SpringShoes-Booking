package com.team28.booking.contracts.events;

public record PaymentRefundedEvent(Long invoiceId, Long bookingId, Double refundAmount) {
}
