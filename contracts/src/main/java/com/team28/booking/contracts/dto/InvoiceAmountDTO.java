package com.team28.booking.contracts.dto;

import java.math.BigDecimal;

public record InvoiceAmountDTO(Long bookingId, BigDecimal amount) {
}
