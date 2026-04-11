package com.team28.booking.invoice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team28.booking.invoice.dto.AppliedDiscountDTO;
import com.team28.booking.invoice.dto.DiscountUsageDTO;
import com.team28.booking.invoice.dto.InvoiceDetailsDTO;
import com.team28.booking.invoice.dto.ProcessInvoiceRequest;
import com.team28.booking.invoice.dto.RetryInvoiceRequest;
import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.dto.UserInvoiceSummaryDTO;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.model.Invoice.InvoiceStatus;
import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.repository.DiscountRepository;
import com.team28.booking.invoice.repository.DiscountUsageProjection;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import com.team28.booking.invoice.repository.InvoiceRepository;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final DiscountRepository discountRepository;
    private final InvoiceDiscountRepository invoiceDiscountRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          DiscountRepository discountRepository,
                          InvoiceDiscountRepository invoiceDiscountRepository) {
        this.invoiceRepository = invoiceRepository;
        this.discountRepository = discountRepository;
        this.invoiceDiscountRepository = invoiceDiscountRepository;
    }

    // ── Top Used Discounts Report ────────────────────────────────────────────

    private static final int MAX_DISCOUNT_REPORT_LIMIT = 100;

    public List<DiscountUsageDTO> getTopUsedDiscountsReport(Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, MAX_DISCOUNT_REPORT_LIMIT);

        List<DiscountUsageProjection> rows = discountRepository.findTopUsedDiscounts(safeLimit);
        List<DiscountUsageDTO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (DiscountUsageProjection row : rows) {
            boolean expired = row.getExpiryDate() != null && row.getExpiryDate().isBefore(now);
            result.add(new DiscountUsageDTO(
                row.getDiscountId(),
                row.getCode(),
                Discount.DiscountType.valueOf(row.getDiscountType()),
                row.getDiscountValue() == null ? 0.0 : row.getDiscountValue(),
                row.getTimesUsed() == null ? 0 : row.getTimesUsed(),
                row.getTotalDiscountGiven() == null ? 0.0 : row.getTotalDiscountGiven(),
                row.getActive() != null && row.getActive(),
                expired
            ));
        }

        return result;
    }

    // ── Apply Discount to Invoice ────────────────────────────────────────────

    @Transactional
    public Invoice applyDiscountToInvoice(Long invoiceId, Long discountId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        if (invoice.getStatus() == Invoice.InvoiceStatus.COMPLETED
                || invoice.getStatus() == Invoice.InvoiceStatus.REFUNDED) {
            throw new BadRequestException("cannot apply discount to a completed/cancelled invoice");
        }

        Discount discount = discountRepository.findById(discountId)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + discountId));

        if (!Boolean.TRUE.equals(discount.getActive())) {
            throw new BadRequestException("discount is inactive");
        }

        LocalDateTime now = LocalDateTime.now();
        if (discount.getExpiryDate() == null || !discount.getExpiryDate().isAfter(now)) {
            throw new BadRequestException("discount is expired");
        }

        int currentUses = discount.getCurrentUses() == null ? 0 : discount.getCurrentUses();
        int maxUses = discount.getMaxUses() == null ? 0 : discount.getMaxUses();
        if (currentUses >= maxUses) {
            throw new BadRequestException("discount usage limit reached");
        }

        if (invoiceDiscountRepository.existsByInvoiceIdAndDiscountId(invoiceId, discountId)) {
            throw new BadRequestException("discount already applied");
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
    
    public InvoiceDetailsDTO getInvoiceDetails(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdWithDiscounts(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        List<AppliedDiscountDTO> appliedDiscounts = invoice.getInvoiceDiscounts().stream()
                .map(this::mapAppliedDiscount)
                .toList();

        double totalDiscount = invoice.getInvoiceDiscounts().stream()
                .map(InvoiceDiscount::getDiscountApplied)
                .filter(Objects::nonNull)
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

    // Search invoices by status and date range
    // Converts enum to string for native SQL query
    public List<Invoice> searchInvoices(InvoiceStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }
        String statusStr = status != null ? status.name() : null;
        return invoiceRepository.searchInvoices(statusStr, startDate, endDate);
    }

    @Transactional
    public Invoice processRefund(Long invoiceId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Refund reason must not be blank");
        }

        Invoice invoice = getInvoiceById(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            throw new BadRequestException("Invoice must be COMPLETED to process refund");
        }

        invoice.setStatus(InvoiceStatus.REFUNDED);

        Map<String, Object> transactionDetails = invoice.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }
        transactionDetails.put("refundReason", reason);
        transactionDetails.put("refundedAt", LocalDateTime.now().toString());
        invoice.setTransactionDetails(transactionDetails);

        return invoiceRepository.save(invoice);
    }

    public UserInvoiceSummaryDTO getUserInvoiceSummary(Long userId) {
        Long userExists = invoiceRepository.findUserById(userId);
        if (userExists == null) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        List<Object[]> results = invoiceRepository.getInvoiceSummaryByUserId(userId);

        Map<String, Double> methodBreakdown = new HashMap<>();
        int totalInvoices = 0;
        double totalAmount = 0.0;

        for (Object[] row : results) {
            String method = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double amount = ((Number) row[2]).doubleValue();

            methodBreakdown.put(method, amount);
            totalInvoices += count;
            totalAmount += amount;
        }

        return new UserInvoiceSummaryDTO(userId, totalInvoices, totalAmount, methodBreakdown);
    }

    // ── S5-F4: Process Invoice for Booking ──────────────────────────────────

    @Transactional
    public Invoice processInvoiceForBooking(ProcessInvoiceRequest request) {
        // 1. Fetch booking from the shared bookings table (cross-service native SQL)
        List<Object[]> rows = invoiceRepository.findBookingDetails(request.getBookingId());
        if (rows == null || rows.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found with id: " + request.getBookingId());
        }

        Object[] booking = rows.get(0);
        String bookingStatus = (String) booking[0];
        if (!"COMPLETED".equals(bookingStatus)) {
            throw new BadRequestException("Booking must be COMPLETED before processing an invoice");
        }

        // 2. Ensure no invoice already exists for this booking
        if (invoiceRepository.existsByBookingId(request.getBookingId())) {
            throw new BadRequestException("An invoice already exists for booking id: " + request.getBookingId());
        }

        Double totalPrice = ((Number) booking[1]).doubleValue();

        // 3. Build the invoice
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

        // 4. Simulate processing — mark completed immediately
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

        // Optionally update payment method
        if (request.getMethod() != null && !request.getMethod().isBlank()) {
            invoice.setMethod(Invoice.PaymentMethod.valueOf(request.getMethod()));
        }

        // Update transactionDetails
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

        // Simulate processing — mark completed
        invoice.setStatus(Invoice.InvoiceStatus.COMPLETED);
        details.put("completedAt", LocalDateTime.now().toString());

        return invoiceRepository.save(invoice);
    }
}
