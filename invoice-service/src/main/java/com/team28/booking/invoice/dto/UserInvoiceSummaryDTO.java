package com.team28.booking.invoice.dto;

import java.util.Map;

public record UserInvoiceSummaryDTO(
        Long userId,
        Integer totalInvoices,
        Double totalAmount,
        Map<String, Double> methodBreakdown
) {}
