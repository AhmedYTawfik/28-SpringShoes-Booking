package com.team28.booking.invoice.service;

import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.model.Invoice.InvoiceStatus;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    // Search invoices by status and date range
    // Converts enum to string for native SQL query
    public List<Invoice> searchInvoices(InvoiceStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        String statusStr = status != null ? status.name() : null;
        return invoiceRepository.searchInvoices(statusStr, startDate, endDate);
    }
}