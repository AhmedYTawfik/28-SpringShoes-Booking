package com.team28.booking.invoice.dto;

import java.math.BigDecimal;

public record ServiceTypeRevenueDTO(
        String serviceType,
        BigDecimal totalRevenue,
        BigDecimal totalCancellationFees,
        Long invoiceCount
) {
    public static ServiceTypeRevenueDTOBuilder builder() { return new ServiceTypeRevenueDTOBuilder(); }
}
