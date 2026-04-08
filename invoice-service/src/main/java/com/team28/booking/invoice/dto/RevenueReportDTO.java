package com.team28.booking.invoice.dto;

import java.time.LocalDate;

public class RevenueReportDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalRevenue;
    private Long totalInvoices;
    private Long completedInvoices;
    private Double refundedAmount;
    private Double netRevenue;
    private Double averageInvoiceAmount;

    public RevenueReportDTO(LocalDate startDate, LocalDate endDate,
                            Double totalRevenue, Long totalInvoices,
                            Long completedInvoices, Double refundedAmount,
                            Double netRevenue, Double averageInvoiceAmount) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalRevenue = totalRevenue;
        this.totalInvoices = totalInvoices;
        this.completedInvoices = completedInvoices;
        this.refundedAmount = refundedAmount;
        this.netRevenue = netRevenue;
        this.averageInvoiceAmount = averageInvoiceAmount;
    }

    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Double getTotalRevenue() { return totalRevenue; }
    public Long getTotalInvoices() { return totalInvoices; }
    public Long getCompletedInvoices() { return completedInvoices; }
    public Double getRefundedAmount() { return refundedAmount; }
    public Double getNetRevenue() { return netRevenue; }
    public Double getAverageInvoiceAmount() { return averageInvoiceAmount; }
}
