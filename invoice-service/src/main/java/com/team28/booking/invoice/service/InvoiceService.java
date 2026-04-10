package com.team28.booking.invoice.service;

import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.repository.DiscountRepository;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final DiscountRepository discountRepository;
    private final InvoiceDiscountRepository invoiceDiscountRepository;

    // Constructor injection
    public InvoiceService(InvoiceRepository invoiceRepository,
                          DiscountRepository discountRepository,
                          InvoiceDiscountRepository invoiceDiscountRepository) {
        this.invoiceRepository = invoiceRepository;
        this.discountRepository = discountRepository;
        this.invoiceDiscountRepository = invoiceDiscountRepository;
    }

    @Transactional
    public Invoice applyDiscountToInvoice(Long invoiceId, Long discountId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invoice not found with id: " + invoiceId));

        if (invoice.getStatus() == Invoice.InvoiceStatus.COMPLETED
                || invoice.getStatus() == Invoice.InvoiceStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply discount to a completed/cancelled invoice");
        }

        Discount discount = discountRepository.findById(discountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Discount not found with id: " + discountId));

        if (!Boolean.TRUE.equals(discount.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discount is inactive");
        }

        LocalDateTime now = LocalDateTime.now();
        if (discount.getExpiryDate() == null || !discount.getExpiryDate().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discount is expired");
        }

        int currentUses = discount.getCurrentUses() == null ? 0 : discount.getCurrentUses();
        int maxUses = discount.getMaxUses() == null ? 0 : discount.getMaxUses();
        if (currentUses >= maxUses) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discount usage limit reached");
        }

        if (invoiceDiscountRepository.existsByInvoiceIdAndDiscountId(invoiceId, discountId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discount already applied");
        }

        double discountApplied;
        if (discount.getDiscountType() == Discount.DiscountType.PERCENTAGE) {
            discountApplied = invoice.getAmount() * discount.getDiscountValue() / 100.0;
        } else {
            discountApplied = discount.getDiscountValue();
        }
        discountApplied = Math.min(discountApplied, invoice.getAmount());

        InvoiceDiscount invoiceDiscount = new InvoiceDiscount();
        invoiceDiscount.setInvoice(invoice);
        invoiceDiscount.setDiscount(discount);
        invoiceDiscount.setDiscountApplied(discountApplied);
        invoiceDiscount.setAppliedAt(now);

        invoiceDiscountRepository.save(invoiceDiscount);

        discount.setCurrentUses(currentUses + 1);
        discountRepository.save(discount);

        invoice.getInvoiceDiscounts().add(invoiceDiscount);
        return invoice;
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
}
