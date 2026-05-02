package com.team28.booking.invoice.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class PartialRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(BigDecimal invoiceAmount,
                                         Map<String, Object> bookingData,
                                         String reason) {
        BigDecimal half = invoiceAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new RefundResult(half, half, "partial_refund");
    }
}
