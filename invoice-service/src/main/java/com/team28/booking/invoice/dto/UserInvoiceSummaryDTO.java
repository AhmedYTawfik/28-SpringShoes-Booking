package com.team28.booking.invoice.dto;

import java.math.BigDecimal;
import java.util.Map;

public record UserInvoiceSummaryDTO(
        Long userId,
        Integer totalInvoices,
        BigDecimal totalAmount,
        Map<String, BigDecimal> methodBreakdown
) {
    public static UserInvoiceSummaryDTOBuilder builder() { return new UserInvoiceSummaryDTOBuilder(); }
}
