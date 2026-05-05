package com.team28.booking.invoice.dto;

import java.math.BigDecimal;

public class PaymentMethodAnalyticsDTO {
    private String method;
    private long successCount;
    private long failureCount;
    private double successRate;
    private BigDecimal totalAmount;

    public PaymentMethodAnalyticsDTO() {}

    public PaymentMethodAnalyticsDTO(String method, long successCount, long failureCount, double successRate, BigDecimal totalAmount) {
        this.method = method;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.successRate = successRate;
        this.totalAmount = totalAmount;
    }

    public String getMethod() { return method; }
    public long getSuccessCount() { return successCount; }
    public long getFailureCount() { return failureCount; }
    public double getSuccessRate() { return successRate; }
    public BigDecimal getTotalAmount() { return totalAmount; }

    public void setMethod(String method) { this.method = method; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
