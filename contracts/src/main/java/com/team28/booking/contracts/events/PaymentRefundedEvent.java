package com.team28.booking.contracts.events;

import java.math.BigDecimal;

public record PaymentRefundedEvent(Long invoiceId, Long bookingId, BigDecimal refundAmount) {
}
