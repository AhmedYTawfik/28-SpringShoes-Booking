package com.team28.booking.invoice.dto;

import java.math.BigDecimal;

public record ServiceTypeRevenueDTO(
        String specialty,
        BigDecimal totalRevenue,
        BigDecimal cancellationFeeRevenue,
        BigDecimal netBookingRevenue,
        Long bookingCount,
        BigDecimal cancellationRate
) {
    public static ServiceTypeRevenueDTOBuilder builder() { return new ServiceTypeRevenueDTOBuilder(); }
}
