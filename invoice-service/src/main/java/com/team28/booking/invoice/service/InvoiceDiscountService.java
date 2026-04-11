package com.team28.booking.invoice.service;

import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvoiceDiscountService {

    private final InvoiceDiscountRepository invoiceDiscountRepository;

    public InvoiceDiscountService(InvoiceDiscountRepository invoiceDiscountRepository) {
        this.invoiceDiscountRepository = invoiceDiscountRepository;
    }

    // Create
    public InvoiceDiscount createInvoiceDiscount(InvoiceDiscount invoiceDiscount) {
        return invoiceDiscountRepository.save(invoiceDiscount);
    }

    // Read by ID
    public InvoiceDiscount getInvoiceDiscountById(Long id) {
        return invoiceDiscountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceDiscount not found with id: " + id));
    }

    // Read all
    public List<InvoiceDiscount> getAllInvoiceDiscounts() {
        return invoiceDiscountRepository.findAll();
    }

    // Update
    public InvoiceDiscount updateInvoiceDiscount(Long id, InvoiceDiscount updatedInvoiceDiscount) {
        InvoiceDiscount existing = getInvoiceDiscountById(id);
        existing.setDiscountApplied(updatedInvoiceDiscount.getDiscountApplied());
        existing.setAppliedAt(updatedInvoiceDiscount.getAppliedAt());
        existing.setInvoice(updatedInvoiceDiscount.getInvoice());
        existing.setDiscount(updatedInvoiceDiscount.getDiscount());
        return invoiceDiscountRepository.save(existing);
    }

    // Delete
    public void deleteInvoiceDiscount(Long id) {
        invoiceDiscountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceDiscount not found with id: " + id));
        invoiceDiscountRepository.deleteById(id);
    }
}