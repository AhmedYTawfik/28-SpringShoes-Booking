package com.team28.booking.invoice.service;

import com.team28.booking.invoice.dto.UserInvoiceSummaryDTO;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + id));
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found with id: " + id));
        invoiceRepository.deleteById(id);
    }

    // Get User Invoice Summary (DTO)
    // a) Verify user exists - throw 404 if not found
    // b) Query payment data grouped by method
    // c) Build method breakdown map
    // d) Calculate totals
    // e) Build and return DTO
    public UserInvoiceSummaryDTO getUserInvoiceSummary(Long userId) {
        // Verify user exists
        Long userExists = invoiceRepository.findUserById(userId);
        if (userExists == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        }

        // Query payment data grouped by method
        List<Object[]> results = invoiceRepository.getInvoiceSummaryByUserId(userId);

        // Build method breakdown map and calculate totals
        Map<String, Double> methodBreakdown = new HashMap<>();
        int totalInvoices = 0;
        double totalAmount = 0.0;

        for (Object[] row : results) {
            String method = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            Double amount = ((Number) row[2]).doubleValue();

            methodBreakdown.put(method, amount);
            totalInvoices += count;
            totalAmount += amount;
        }

        // Build and return DTO
        return new UserInvoiceSummaryDTO(userId, totalInvoices, totalAmount, methodBreakdown);
    }
}