package com.team28.booking.invoice.service;

import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvoiceDiscountService {

    private final InvoiceDiscountRepository invoiceDiscountRepository;
    private final CacheInvalidator cacheInvalidator;

    public InvoiceDiscountService(InvoiceDiscountRepository invoiceDiscountRepository,
                                  CacheInvalidator cacheInvalidator) {
        this.invoiceDiscountRepository = invoiceDiscountRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    public InvoiceDiscount createInvoiceDiscount(InvoiceDiscount invoiceDiscount) {
        InvoiceDiscount saved = invoiceDiscountRepository.save(invoiceDiscount);
        cacheInvalidator.wildcardDelete("invoice-service::invoice-discount::*");
        return saved;
    }

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). */
    @Cacheable(cacheNames = "invoice-service::invoice-discount", key = "#id")
    public InvoiceDiscount getInvoiceDiscountById(Long id) {
        return invoiceDiscountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceDiscount not found with id: " + id));
    }

    public List<InvoiceDiscount> getAllInvoiceDiscounts() {
        return invoiceDiscountRepository.findAll();
    }

    public InvoiceDiscount updateInvoiceDiscount(Long id, InvoiceDiscount updatedInvoiceDiscount) {
        InvoiceDiscount existing = findById(id);
        existing.setDiscountApplied(updatedInvoiceDiscount.getDiscountApplied());
        existing.setAppliedAt(updatedInvoiceDiscount.getAppliedAt());
        existing.setInvoice(updatedInvoiceDiscount.getInvoice());
        existing.setDiscount(updatedInvoiceDiscount.getDiscount());
        InvoiceDiscount saved = invoiceDiscountRepository.save(existing);
        cacheInvalidator.deleteKey("invoice-service::invoice-discount::" + id);
        return saved;
    }

    public void deleteInvoiceDiscount(Long id) {
        findById(id);
        invoiceDiscountRepository.deleteById(id);
        cacheInvalidator.deleteKey("invoice-service::invoice-discount::" + id);
    }

    /** Non-cached DB fetch used by all write paths (§4.4.4). */
    InvoiceDiscount findById(Long id) {
        return invoiceDiscountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceDiscount not found with id: " + id));
    }
}
