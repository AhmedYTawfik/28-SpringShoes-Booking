package com.team28.booking.invoice.service;

import com.team28.booking.invoice.exception.ConflictException;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs SELECT…FOR UPDATE on the PENDING invoice and flips status to PROCESSING
 * in its own committed transaction so concurrent callers see PROCESSING immediately.
 */
@Service
public class InvoiceStatusLockService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceStatusLockService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Invoice lockAndSetProcessing(Long bookingId) {
        Invoice invoice = invoiceRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending invoice — saga has not yet run for bookingId: " + bookingId));

        Invoice.InvoiceStatus status = invoice.getStatus();
        if (status != Invoice.InvoiceStatus.PENDING) {
            throw new ConflictException(
                    "Concurrent or duplicate payment request. " + "Invoice is already " + status + " for bookingId: " + bookingId);
        }

        invoice.setStatus(Invoice.InvoiceStatus.PROCESSING);
        return invoiceRepository.saveAndFlush(invoice);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertToPending(Long invoiceId) {
        invoiceRepository.findById(invoiceId).ifPresent(inv -> {
            inv.setStatus(Invoice.InvoiceStatus.PENDING);
            invoiceRepository.saveAndFlush(inv);
        });
    }
}
