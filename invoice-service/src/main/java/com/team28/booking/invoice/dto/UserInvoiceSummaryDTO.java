package com.team28.booking.invoice.dto;

import java.util.Map;

public class UserInvoiceSummaryDTO {
    private Long userId;
    private Integer totalInvoices;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserInvoiceSummaryDTO() {}

    public UserInvoiceSummaryDTO(Long userId, Integer totalInvoices, Double totalAmount, Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalInvoices = totalInvoices;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getTotalInvoices() { return totalInvoices; }
    public void setTotalInvoices(Integer totalInvoices) { this.totalInvoices = totalInvoices; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }
}
