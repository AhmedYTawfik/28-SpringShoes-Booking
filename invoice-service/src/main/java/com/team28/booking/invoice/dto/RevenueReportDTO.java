package com.team28.booking.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueReportDTO(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalRevenue,
        Long totalInvoices,
        Long completedInvoices,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        BigDecimal averageInvoiceAmount
) {
    public static RevenueReportDTOBuilder builder() { return new RevenueReportDTOBuilder(); }
}
