package com.team28.booking.invoice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team28.booking.invoice.adapter.ObjectArrayDtoAdapter;
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
import com.team28.booking.invoice.observer.MongoEventLogger;
import com.team28.booking.invoice.observer.Observable;
import com.team28.booking.invoice.repository.DiscountRepository;
import com.team28.booking.invoice.repository.DiscountUsageProjection;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import com.team28.booking.invoice.repository.InvoiceRepository;

import jakarta.annotation.PostConstruct;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class InvoiceService extends Observable {

    private final InvoiceRepository invoiceRepository;
    private final DiscountRepository discountRepository;
    private final InvoiceDiscountRepository invoiceDiscountRepository;
    private final MongoEventLogger mongoEventLogger;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          DiscountRepository discountRepository,
                          InvoiceDiscountRepository invoiceDiscountRepository,
                          MongoEventLogger mongoEventLogger, ObjectArrayDtoAdapter objectArrayDtoAdapter) {
                                  this.invoiceRepository = invoiceRepository;
                                  this.discountRepository = discountRepository;
                                  this.invoiceDiscountRepository = invoiceDiscountRepository;
                                  this.mongoEventLogger = mongoEventLogger;
                                  this.objectArrayDtoAdapter = objectArrayDtoAdapter;
    }

    @PostConstruct
    void registerObservers() {
        register(mongoEventLogger);
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
            result.add(DiscountUsageDTO.builder()
                .discountId(row.getDiscountId())
                .code(row.getCode())
                .discountType(Discount.DiscountType.valueOf(row.getDiscountType()))
                .discountValue(row.getDiscountValue() != null ? row.getDiscountValue() : BigDecimal.ZERO)
                .timesUsed(row.getTimesUsed() == null ? 0 : row.getTimesUsed())
                .totalDiscountGiven(row.getTotalDiscountGiven() != null ? row.getTotalDiscountGiven() : BigDecimal.ZERO)
                .active(row.getActive() != null && row.getActive())
                .expired(expired)
                .build());
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

        BigDecimal discountApplied;
        if (discount.getDiscountType() == Discount.DiscountType.PERCENTAGE) {
            BigDecimal invoiceAmount = invoice.getAmount();
            discountApplied = (invoiceAmount != null ? invoiceAmount : BigDecimal.ZERO)
                    .multiply(discount.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discountApplied = discount.getDiscountValue();
        }
        discountApplied = discountApplied.min(invoice.getAmount());

        InvoiceDiscount invoiceDiscount = new InvoiceDiscount();
        invoiceDiscount.setInvoice(invoice);
        invoiceDiscount.setDiscount(discount);
        invoiceDiscount.setDiscountApplied(discountApplied);
        invoiceDiscount.setAppliedAt(now);

        invoiceDiscountRepository.save(invoiceDiscount);

        discount.setCurrentUses(currentUses + 1);
        discountRepository.save(discount);

        invoice.getInvoiceDiscounts().add(invoiceDiscount);
        Map<String, Object> payload = invoicePayload(invoice);
        payload.put("discountId", discountId);
        payload.put("discountApplied", discountApplied);
        emitAfterCommit("DISCOUNT_APPLIED", payload);
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
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        Map<String, Object> payload = invoicePayload(invoice);
        invoiceRepository.deleteById(id);
        emitAfterCommit("INVOICE_DELETED", payload);
    }
    
    public InvoiceDetailsDTO getInvoiceDetails(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdWithDiscounts(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        List<AppliedDiscountDTO> appliedDiscounts = invoice.getInvoiceDiscounts().stream()
                .map(this::mapAppliedDiscount)
                .toList();

        BigDecimal totalDiscount = invoice.getInvoiceDiscounts().stream()
                .map(InvoiceDiscount::getDiscountApplied)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal originalAmount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount = originalAmount.subtract(totalDiscount);

        return InvoiceDetailsDTO.builder()
                .invoiceId(invoice.getId())
                .bookingId(invoice.getBookingId())
                .userId(invoice.getUserId())
                .originalAmount(originalAmount)
                .method(invoice.getMethod())
                .status(invoice.getStatus())
                .transactionDetails(invoice.getTransactionDetails())
                .appliedDiscounts(appliedDiscounts)
                .totalDiscount(totalDiscount)
                .finalAmount(finalAmount)
                .build();
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

        Invoice saved = invoiceRepository.save(invoice);
        Map<String, Object> payload = invoicePayload(saved);
        payload.put("details", Map.of("refundReason", reason));
        emitAfterCommit("REFUNDED", payload);
        return saved;
    }

    public UserInvoiceSummaryDTO getUserInvoiceSummary(Long userId) {
        Long userExists = invoiceRepository.findUserById(userId);
        if (userExists == null) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        List<Object[]> results = invoiceRepository.getInvoiceSummaryByUserId(userId);
        return objectArrayDtoAdapter.toUserInvoiceSummaryDTO(userId, results);
    }

    // ── S5-F4: Process Invoice for Booking ──────────────────────────────────

    @Transactional
    public Invoice processInvoiceForBooking(ProcessInvoiceRequest request) {
        return processInvoiceForBooking(request, false);
    }

    @Transactional
    public Invoice processInvoiceForBooking(ProcessInvoiceRequest request, boolean simulateFailure) {
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

        BigDecimal totalPrice = booking[1] != null
                ? new BigDecimal(booking[1].toString())
                : BigDecimal.ZERO;

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
        details.put("cancellationFee", 0);
        invoice.setTransactionDetails(details);

        if (simulateFailure) {
            invoice.setStatus(Invoice.InvoiceStatus.FAILED);
            details.put("failedAt", LocalDateTime.now().toString());
            details.put("reason", "simulated_gateway_failure");

            Invoice failed = invoiceRepository.save(invoice);
            Map<String, Object> payload = invoicePayload(failed);
            payload.put("details", Map.of("reason", "simulated_gateway_failure"));
            emitAfterCommit("FAILED", payload);
            return failed;
        }

        invoice.setStatus(Invoice.InvoiceStatus.PENDING);
        Invoice created = invoiceRepository.save(invoice);
        emitAfterCommit("CREATED", invoicePayload(created));

        created.setStatus(Invoice.InvoiceStatus.COMPLETED);
        details.put("completedAt", LocalDateTime.now().toString());
        created.setTransactionDetails(details);

        Invoice completed = invoiceRepository.save(created);
        emitAfterCommit("COMPLETED", invoicePayload(completed));
        return completed;
    }

    // ── S5-F6: Revenue Report by Date Range ─────────────────────────────────

    public RevenueReportDTO getRevenueReport(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = endDate.atTime(23, 59, 59);

        Object[] row = invoiceRepository.getRevenueStats(from, to);
        return objectArrayDtoAdapter.toRevenueReportDTO(startDate, endDate, row);
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

        Invoice saved = invoiceRepository.save(invoice);
        Map<String, Object> payload = invoicePayload(saved);
        payload.put("retryCount", retryCount);
        emitAfterCommit("RETRY_ATTEMPTED", payload);
        return saved;
    }

    protected void emitAfterCommit(String action, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyObservers(action, payload);
                }
            });
        } else {
            notifyObservers(action, payload);
        }
    }

    protected Map<String, Object> invoicePayload(Invoice invoice) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoiceId", invoice.getId());
        payload.put("bookingId", invoice.getBookingId());
        payload.put("userId", invoice.getUserId());
        payload.put("method", invoice.getMethod() != null ? invoice.getMethod().name() : null);
        payload.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);
        payload.put("amount", invoice.getAmount());
        return payload;
    }
}
