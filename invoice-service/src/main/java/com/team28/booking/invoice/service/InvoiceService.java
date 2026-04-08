package com.team28.booking.invoice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.team28.booking.invoice.dto.DiscountUsageDTO;
import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.DiscountRepository;
import com.team28.booking.invoice.repository.InvoiceRepository;



@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final DiscountRepository discountRepository;

    // Constructor injection
    // public InvoiceService(InvoiceRepository invoiceRepository) {
    //     this.invoiceRepository = invoiceRepository;
    // }
    public InvoiceService(InvoiceRepository invoiceRepository, DiscountRepository discountRepository) {
        this.invoiceRepository = invoiceRepository;
        this.discountRepository = discountRepository;
    }

    public List<DiscountUsageDTO> getTopUsedDiscountsReport(Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;

        List<Object[]> rows = discountRepository.findTopUsedDiscounts(safeLimit);
        List<DiscountUsageDTO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Object[] row : rows) {
            Long discountId = ((Number) row[0]).longValue();
            String code = (String) row[1];
            Discount.DiscountType discountType = Discount.DiscountType.valueOf(row[2].toString());
            Double discountValue = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
            Integer timesUsed = row[4] == null ? 0 : ((Number) row[4]).intValue();
            Double totalDiscountGiven = row[5] == null ? 0.0 : ((Number) row[5]).doubleValue();
            Boolean active = row[6] != null && (Boolean) row[6];
            LocalDateTime expiryDate;
            if (row[7] instanceof java.sql.Timestamp ts) {
                expiryDate = ts.toLocalDateTime();
            } else {
                expiryDate = (LocalDateTime) row[7];
            }
            boolean expired = expiryDate != null && expiryDate.isBefore(now);

            result.add(new DiscountUsageDTO(
                discountId,
                code,
                discountType,
                discountValue,
                timesUsed,
                totalDiscountGiven,
                active,
                expired
            ));
        }

        return result;
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