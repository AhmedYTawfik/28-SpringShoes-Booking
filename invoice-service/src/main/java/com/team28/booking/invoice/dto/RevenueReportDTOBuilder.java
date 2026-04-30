package com.team28.booking.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RevenueReportDTOBuilder {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalRevenue;
    private Long totalInvoices;
    private Long completedInvoices;
    private BigDecimal refundedAmount;
    private BigDecimal netRevenue;
    private BigDecimal averageInvoiceAmount;

    public RevenueReportDTOBuilder startDate(LocalDate startDate) { this.startDate = startDate; return this; }
    public RevenueReportDTOBuilder endDate(LocalDate endDate) { this.endDate = endDate; return this; }
    public RevenueReportDTOBuilder totalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public RevenueReportDTOBuilder totalInvoices(Long totalInvoices) { this.totalInvoices = totalInvoices; return this; }
    public RevenueReportDTOBuilder completedInvoices(Long completedInvoices) { this.completedInvoices = completedInvoices; return this; }
    public RevenueReportDTOBuilder refundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; return this; }
    public RevenueReportDTOBuilder netRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; return this; }
    public RevenueReportDTOBuilder averageInvoiceAmount(BigDecimal averageInvoiceAmount) { this.averageInvoiceAmount = averageInvoiceAmount; return this; }

    public RevenueReportDTO build() {
        return new RevenueReportDTO(startDate, endDate, totalRevenue, totalInvoices,
                completedInvoices, refundedAmount, netRevenue, averageInvoiceAmount);
    }
}
