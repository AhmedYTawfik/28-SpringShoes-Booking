package com.team28.booking.invoice.dto;

import java.math.BigDecimal;

public class ServiceTypeRevenueDTOBuilder {
    private String specialty;
    private BigDecimal totalRevenue;
    private BigDecimal cancellationFeeRevenue;
    private BigDecimal netBookingRevenue;
    private Long bookingCount;
    private BigDecimal cancellationRate;

    public ServiceTypeRevenueDTOBuilder specialty(String specialty) { this.specialty = specialty; return this; }
    public ServiceTypeRevenueDTOBuilder totalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public ServiceTypeRevenueDTOBuilder cancellationFeeRevenue(BigDecimal cancellationFeeRevenue) { this.cancellationFeeRevenue = cancellationFeeRevenue; return this; }
    public ServiceTypeRevenueDTOBuilder netBookingRevenue(BigDecimal netBookingRevenue) { this.netBookingRevenue = netBookingRevenue; return this; }
    public ServiceTypeRevenueDTOBuilder bookingCount(Long bookingCount) { this.bookingCount = bookingCount; return this; }
    public ServiceTypeRevenueDTOBuilder cancellationRate(BigDecimal cancellationRate) { this.cancellationRate = cancellationRate; return this; }

    public ServiceTypeRevenueDTO build() {
        return new ServiceTypeRevenueDTO(specialty, totalRevenue, cancellationFeeRevenue,
                netBookingRevenue, bookingCount, cancellationRate);
    }
}
