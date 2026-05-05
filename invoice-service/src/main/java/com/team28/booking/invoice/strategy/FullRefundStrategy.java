package com.team28.booking.invoice.strategy;

import java.math.BigDecimal;
import java.util.Map;

public class FullRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(BigDecimal invoiceAmount,
                                         Map<String, Object> bookingData,
                                         String reason) {
        return new RefundResult(invoiceAmount, BigDecimal.ZERO, "full_refund");
    }
}
