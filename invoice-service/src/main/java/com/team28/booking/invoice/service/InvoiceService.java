package com.team28.booking.invoice.service;

import com.team28.booking.invoice.dto.ProcessInvoiceRequest;
import com.team28.booking.invoice.dto.RetryInvoiceRequest;
import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    public Invoice createInvoice(Invoice invoice) {
        return invoiceRepository.save(invoice);
    }

    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

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

    public void deleteInvoice(Long id) {
        invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        invoiceRepository.deleteById(id);
    }

    // ── S5-F4: Process Invoice for Booking ──────────────────────────────────

    @Transactional
    public Invoice processInvoiceForBooking(ProcessInvoiceRequest request) {
        List<Object[]> rows = invoiceRepository.findBookingDetails(request.getBookingId());
        if (rows == null || rows.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found with id: " + request.getBookingId());
        }

        Object[] booking = rows.get(0);
        String bookingStatus = (String) booking[0];
        if (!"COMPLETED".equals(bookingStatus)) {
            throw new BadRequestException("Booking must be COMPLETED before processing an invoice");
        }

        if (invoiceRepository.existsByBookingId(request.getBookingId())) {
            throw new BadRequestException("An invoice already exists for booking id: " + request.getBookingId());
        }

        Double totalPrice = ((Number) booking[1]).doubleValue();

        Invoice invoice = new Invoice();
        invoice.setBookingId(request.getBookingId());
        invoice.setUserId(request.getUserId());
        invoice.setAmount(totalPrice);
        invoice.setMethod(Invoice.PaymentMethod.valueOf(request.getMethod()));
        invoice.setCreatedAt(LocalDateTime.now());

        Map<String, Object> details = new HashMap<>();
        details.put("gateway", "internal");
        details.put("initiatedAt", LocalDateTime.now().toString());
        invoice.setTransactionDetails(details);

        invoice.setStatus(Invoice.InvoiceStatus.COMPLETED);
        details.put("completedAt", LocalDateTime.now().toString());

        return invoiceRepository.save(invoice);
    }

    // ── S5-F6: Revenue Report by Date Range ─────────────────────────────────

    public RevenueReportDTO getRevenueReport(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = endDate.atTime(23, 59, 59);

        Object[] row = invoiceRepository.getRevenueStats(from, to);

        double totalRevenue         = row[0] == null ? 0.0 : ((Number) row[0]).doubleValue();
        long   totalInvoices        = row[1] == null ? 0L  : ((Number) row[1]).longValue();
        long   completedInvoices    = row[2] == null ? 0L  : ((Number) row[2]).longValue();
        double refundedAmount       = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
        double averageInvoiceAmount = row[4] == null ? 0.0 : ((Number) row[4]).doubleValue();
        double netRevenue           = totalRevenue - refundedAmount;

        return new RevenueReportDTO(startDate, endDate, totalRevenue, totalInvoices,
                completedInvoices, refundedAmount, netRevenue, averageInvoiceAmount);
    }

    // ── S5-F7: Retry Failed Invoice ──────────────────────────────────────────

    @Transactional
    public Invoice retryFailedInvoice(Long id, RetryInvoiceRequest request) {
        Invoice invoice = getInvoiceById(id);

        if (invoice.getStatus() != Invoice.InvoiceStatus.FAILED) {
            throw new BadRequestException("Only FAILED invoices can be retried");
        }

        if (request.getMethod() != null && !request.getMethod().isBlank()) {
            invoice.setMethod(Invoice.PaymentMethod.valueOf(request.getMethod()));
        }

        Map<String, Object> details = invoice.getTransactionDetails();
        if (details == null) {
            details = new HashMap<>();
        }
        details.put("retryAt", LocalDateTime.now().toString());
        int retryCount = details.containsKey("retryCount")
                ? ((Number) details.get("retryCount")).intValue() + 1
                : 1;
        details.put("retryCount", retryCount);
        invoice.setTransactionDetails(details);

        invoice.setStatus(Invoice.InvoiceStatus.COMPLETED);
        details.put("completedAt", LocalDateTime.now().toString());

        return invoiceRepository.save(invoice);
    }
}
