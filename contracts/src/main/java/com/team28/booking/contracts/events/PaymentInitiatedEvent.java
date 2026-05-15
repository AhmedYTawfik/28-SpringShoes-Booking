package com.team28.booking.contracts.events;

import java.math.BigDecimal;

public record PaymentInitiatedEvent(Long invoiceId, Long bookingId, BigDecimal amount) {
}
