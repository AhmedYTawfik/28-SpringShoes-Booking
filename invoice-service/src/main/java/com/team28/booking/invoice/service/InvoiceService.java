package com.team28.booking.invoice.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.team28.booking.invoice.dto.AppliedDiscountDTO;
import com.team28.booking.invoice.dto.InvoiceDetailsDTO;
import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.repository.InvoiceRepository;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    // Constructor injection
    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    // Create
    public Invoice createInvoice(Invoice invoice) {
        return invoiceRepository.save(invoice);
    }

    // Read by ID
    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
    }

    // Read all
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    // Update
    public Invoice updateInvoice(Long id, Invoice updatedInvoice) {
        Invoice existing = getInvoiceById(id);
        existing.setBookingId(updatedInvoice.getBookingId());
        existing.setUserId(updatedInvoice.getUserId());
        existing.setAmount(updatedInvoice.getAmount());
        existing.setMethod(updatedInvoice.getMethod());
        existing.setStatus(updatedInvoice.getStatus());
        existing.setTransactionDetails(updatedInvoice.getTransactionDetails());
        existing.setCreatedAt(updatedInvoice.getCreatedAt());
        return invoiceRepository.save(existing);
    }

    // Delete
    public void deleteInvoice(Long id) {
        invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
        invoiceRepository.deleteById(id);
    }
    
    public InvoiceDetailsDTO getInvoiceDetails(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdWithDiscounts(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + invoiceId));

        List<AppliedDiscountDTO> appliedDiscounts = invoice.getInvoiceDiscounts().stream()
            .map(this::mapAppliedDiscount)
            .toList();

        double totalDiscount = invoice.getInvoiceDiscounts().stream()
                .map(InvoiceDiscount::getDiscountApplied)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        double originalAmount = invoice.getAmount() == null ? 0.0 : invoice.getAmount();
        double finalAmount = originalAmount - totalDiscount;

        return new InvoiceDetailsDTO(
                invoice.getId(),
                invoice.getBookingId(),
                invoice.getUserId(),
                originalAmount,
                invoice.getMethod(),
                invoice.getStatus(),
                invoice.getTransactionDetails(),
                appliedDiscounts,
                totalDiscount,
                finalAmount
        );
    }

private AppliedDiscountDTO mapAppliedDiscount(InvoiceDiscount invoiceDiscount) {
    Discount discount = invoiceDiscount.getDiscount();
    return new AppliedDiscountDTO(
            discount != null ? discount.getCode() : null,
            discount != null ? discount.getDiscountType() : null,
            invoiceDiscount.getDiscountApplied(),
            invoiceDiscount.getAppliedAt()
    );
}
}