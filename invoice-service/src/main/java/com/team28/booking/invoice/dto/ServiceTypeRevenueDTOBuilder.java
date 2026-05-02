package com.team28.booking.invoice.dto;

import java.math.BigDecimal;

public class ServiceTypeRevenueDTOBuilder {
    private String serviceType;
    private BigDecimal totalRevenue;
    private BigDecimal totalCancellationFees;
    private Long invoiceCount;

    public ServiceTypeRevenueDTOBuilder serviceType(String serviceType) { this.serviceType = serviceType; return this; }
    public ServiceTypeRevenueDTOBuilder totalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public ServiceTypeRevenueDTOBuilder totalCancellationFees(BigDecimal totalCancellationFees) { this.totalCancellationFees = totalCancellationFees; return this; }
    public ServiceTypeRevenueDTOBuilder invoiceCount(Long invoiceCount) { this.invoiceCount = invoiceCount; return this; }

    public ServiceTypeRevenueDTO build() {
        return new ServiceTypeRevenueDTO(serviceType, totalRevenue, totalCancellationFees, invoiceCount);
    }
}
