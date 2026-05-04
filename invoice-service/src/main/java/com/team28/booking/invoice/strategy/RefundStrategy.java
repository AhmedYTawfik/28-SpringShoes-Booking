package com.team28.booking.invoice.strategy;

import java.math.BigDecimal;
import java.util.Map;

public interface RefundStrategy {

    RefundResult calculateRefund(BigDecimal invoiceAmount,
                                  Map<String, Object> bookingData,
                                  String reason);

    default String strategyName() {
        return getClass().getSimpleName();
    }
}
