package com.team28.booking.invoice.mongo;

public class PaymentMethodBreakdown {

    private String method;
    private long successCount;
    private long failureCount;
    private double totalAmount;

    public PaymentMethodBreakdown() {}

    public PaymentMethodBreakdown(String method, long successCount, long failureCount, double totalAmount) {
        this.method = method;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalAmount = totalAmount;
    }

    public String getMethod() { return method; }
    public long getSuccessCount() { return successCount; }
    public long getFailureCount() { return failureCount; }
    public double getTotalAmount() { return totalAmount; }

    public void setMethod(String method) { this.method = method; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}
