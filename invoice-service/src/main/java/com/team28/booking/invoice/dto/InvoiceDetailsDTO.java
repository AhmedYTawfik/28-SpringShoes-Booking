package com.team28.booking.invoice.dto;

import java.util.List;
import java.util.Map;

import com.team28.booking.invoice.model.Invoice;

public record InvoiceDetailsDTO(
        Long invoiceId,
        Long bookingId,
        Long userId,
        Double originalAmount,
        Invoice.PaymentMethod method,
        Invoice.InvoiceStatus status,
        Map<String, Object> transactionDetails,
        List<AppliedDiscountDTO> appliedDiscounts,
        Double totalDiscount,
        Double finalAmount
) {}