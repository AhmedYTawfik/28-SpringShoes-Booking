package com.team28.booking.invoice.dto;

import java.math.BigDecimal;
import java.util.Map;

public class UserInvoiceSummaryDTOBuilder {
    private Long userId;
    private Integer totalInvoices;
    private BigDecimal totalAmount;
    private Map<String, BigDecimal> methodBreakdown;

    public UserInvoiceSummaryDTOBuilder userId(Long userId) { this.userId = userId; return this; }
    public UserInvoiceSummaryDTOBuilder totalInvoices(Integer totalInvoices) { this.totalInvoices = totalInvoices; return this; }
    public UserInvoiceSummaryDTOBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
    public UserInvoiceSummaryDTOBuilder methodBreakdown(Map<String, BigDecimal> methodBreakdown) { this.methodBreakdown = methodBreakdown; return this; }

    public UserInvoiceSummaryDTO build() {
        return new UserInvoiceSummaryDTO(userId, totalInvoices, totalAmount, methodBreakdown);
    }
}
