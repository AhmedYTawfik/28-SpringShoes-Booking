package com.team28.booking.invoice.strategy;

import java.math.BigDecimal;
import java.util.Map;

public class NoRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(BigDecimal invoiceAmount,
                                         Map<String, Object> bookingData,
                                         String reason) {
        return new RefundResult(BigDecimal.ZERO, BigDecimal.ZERO,
                                "booking already started or completed");
    }
}
