package com.team28.booking.invoice.strategy;

import java.math.BigDecimal;

public class RefundResult {

    private final BigDecimal refundAmount;
    private final BigDecimal cancellationFee;
    private final String reasonCode;

    public RefundResult(BigDecimal refundAmount, BigDecimal cancellationFee,
                        String reasonCode) {
        this.refundAmount = refundAmount;
        this.cancellationFee = cancellationFee;
        this.reasonCode = reasonCode;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public BigDecimal getCancellationFee() {
        return cancellationFee;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
