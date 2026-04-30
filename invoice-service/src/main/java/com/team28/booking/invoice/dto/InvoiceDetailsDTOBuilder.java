package com.team28.booking.invoice.dto;

import com.team28.booking.invoice.model.Invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class InvoiceDetailsDTOBuilder {
    private Long invoiceId;
    private Long bookingId;
    private Long userId;
    private BigDecimal originalAmount;
    private Invoice.PaymentMethod method;
    private Invoice.InvoiceStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedDiscountDTO> appliedDiscounts;
    private BigDecimal totalDiscount;
    private BigDecimal finalAmount;

    public InvoiceDetailsDTOBuilder invoiceId(Long invoiceId) { this.invoiceId = invoiceId; return this; }
    public InvoiceDetailsDTOBuilder bookingId(Long bookingId) { this.bookingId = bookingId; return this; }
    public InvoiceDetailsDTOBuilder userId(Long userId) { this.userId = userId; return this; }
    public InvoiceDetailsDTOBuilder originalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; return this; }
    public InvoiceDetailsDTOBuilder method(Invoice.PaymentMethod method) { this.method = method; return this; }
    public InvoiceDetailsDTOBuilder status(Invoice.InvoiceStatus status) { this.status = status; return this; }
    public InvoiceDetailsDTOBuilder transactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; return this; }
    public InvoiceDetailsDTOBuilder appliedDiscounts(List<AppliedDiscountDTO> appliedDiscounts) { this.appliedDiscounts = appliedDiscounts; return this; }
    public InvoiceDetailsDTOBuilder totalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount; return this; }
    public InvoiceDetailsDTOBuilder finalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; return this; }

    public InvoiceDetailsDTO build() {
        return new InvoiceDetailsDTO(invoiceId, bookingId, userId, originalAmount,
                method, status, transactionDetails, appliedDiscounts, totalDiscount, finalAmount);
    }
}
