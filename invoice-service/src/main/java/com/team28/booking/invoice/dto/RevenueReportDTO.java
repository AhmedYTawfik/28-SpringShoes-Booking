package com.team28.booking.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueReportDTO(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalRevenue,
        Long totalTransactions,
        BigDecimal refundedAmount,
        Long refundCount,
        BigDecimal netRevenue,
        BigDecimal averageInvoiceAmount
) {
    public static RevenueReportDTOBuilder builder() { return new RevenueReportDTOBuilder(); }
}
