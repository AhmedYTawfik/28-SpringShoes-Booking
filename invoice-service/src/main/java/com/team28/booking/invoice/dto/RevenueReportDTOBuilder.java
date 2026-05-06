package com.team28.booking.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RevenueReportDTOBuilder {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalRevenue;
    private Long totalTransactions;
    private BigDecimal refundedAmount;
    private Long refundCount;
    private BigDecimal netRevenue;
    private BigDecimal averageInvoice;

    public RevenueReportDTOBuilder startDate(LocalDate startDate) { this.startDate = startDate; return this; }
    public RevenueReportDTOBuilder endDate(LocalDate endDate) { this.endDate = endDate; return this; }
    public RevenueReportDTOBuilder totalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public RevenueReportDTOBuilder totalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; return this; }
    public RevenueReportDTOBuilder refundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; return this; }
    public RevenueReportDTOBuilder refundCount(Long refundCount) { this.refundCount = refundCount; return this; }
    public RevenueReportDTOBuilder netRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; return this; }
    public RevenueReportDTOBuilder averageInvoice(BigDecimal averageInvoice) { this.averageInvoice = averageInvoice; return this; }

    public RevenueReportDTO build() {
        return new RevenueReportDTO(startDate, endDate, totalRevenue, totalTransactions,
                refundedAmount, refundCount, netRevenue, averageInvoice);
    }
}
