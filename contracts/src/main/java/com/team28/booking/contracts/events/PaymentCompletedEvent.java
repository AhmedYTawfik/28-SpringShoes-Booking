package com.team28.booking.contracts.events;

import java.math.BigDecimal;

public record PaymentCompletedEvent(Long invoiceId, Long bookingId, BigDecimal amount) {
}
