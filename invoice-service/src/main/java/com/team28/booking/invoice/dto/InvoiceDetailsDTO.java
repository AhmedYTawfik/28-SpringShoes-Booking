package com.team28.booking.invoice.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.team28.booking.invoice.model.Invoice;

public class InvoiceDetailsDTO {
    private Long invoiceId;
    private Long bookingId;
    private Long userId;
    private Double originalAmount;
    private Invoice.PaymentMethod method;
    private Invoice.InvoiceStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedDiscountDTO> appliedDiscounts = new ArrayList<>();
    private Double totalDiscount;
    private Double finalAmount;

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(Double originalAmount) {
        this.originalAmount = originalAmount;
    }

    public Invoice.PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(Invoice.PaymentMethod method) {
        this.method = method;
    }

    public Invoice.InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(Invoice.InvoiceStatus status) {
        this.status = status;
    }

    public Map<String, Object> getTransactionDetails() {
        return transactionDetails;
    }

    public void setTransactionDetails(Map<String, Object> transactionDetails) {
        this.transactionDetails = transactionDetails;
    }

    public List<AppliedDiscountDTO> getAppliedDiscounts() {
        return appliedDiscounts;
    }

    public void setAppliedDiscounts(List<AppliedDiscountDTO> appliedDiscounts) {
        this.appliedDiscounts = appliedDiscounts;
    }

    public Double getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(Double totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public Double getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(Double finalAmount) {
        this.finalAmount = finalAmount;
    }
}