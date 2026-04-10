package com.team28.booking.invoice.dto;

import java.time.LocalDate;

public record RevenueReportDTO(
        LocalDate startDate,
        LocalDate endDate,
        Double totalRevenue,
        Long totalInvoices,
        Long completedInvoices,
        Double refundedAmount,
        Double netRevenue,
        Double averageInvoiceAmount
) {}
