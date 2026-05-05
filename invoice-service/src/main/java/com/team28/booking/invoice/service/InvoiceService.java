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

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.team28.booking.invoice.adapter.ObjectArrayDtoAdapter;
import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.dto.AppliedDiscountDTO;
import com.team28.booking.invoice.dto.DiscountUsageDTO;
import com.team28.booking.invoice.dto.InvoiceDetailsDTO;
import com.team28.booking.invoice.dto.PaymentMethodAnalyticsDTO;
import com.team28.booking.invoice.dto.ProcessInvoiceRequest;
import com.team28.booking.invoice.dto.RetryInvoiceRequest;
import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.dto.ServiceTypeRevenueDTO;
import com.team28.booking.invoice.dto.UserInvoiceSummaryDTO;
import com.team28.booking.invoice.exception.BadRequestException;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.model.Invoice.InvoiceStatus;
import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.mongo.PaymentAuditEventRepository;
import com.team28.booking.invoice.mongo.PaymentMethodBreakdown;
import com.team28.booking.invoice.observer.MongoEventLogger;
import com.team28.booking.invoice.observer.Observable;
import com.team28.booking.invoice.repository.DiscountRepository;
import com.team28.booking.invoice.repository.DiscountUsageProjection;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import com.team28.booking.invoice.repository.InvoiceRepository;

import jakarta.annotation.PostConstruct;

@Service
public class InvoiceService extends Observable {

    private final InvoiceRepository invoiceRepository;
    private final DiscountRepository discountRepository;
    private final InvoiceDiscountRepository invoiceDiscountRepository;
    private final MongoEventLogger mongoEventLogger;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;
    private final CacheInvalidator cacheInvalidator;
    private final PaymentAuditEventRepository paymentAuditEventRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          DiscountRepository discountRepository,
                          InvoiceDiscountRepository invoiceDiscountRepository,
                          MongoEventLogger mongoEventLogger,
                          ObjectArrayDtoAdapter objectArrayDtoAdapter,
                          CacheInvalidator cacheInvalidator,
                          PaymentAuditEventRepository paymentAuditEventRepository) {
        this.invoiceRepository = invoiceRepository;
        this.discountRepository = discountRepository;
        this.invoiceDiscountRepository = invoiceDiscountRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;
        this.cacheInvalidator = cacheInvalidator;
        this.paymentAuditEventRepository = paymentAuditEventRepository;
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
        Invoice invoice = findById(invoiceId);

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
        invalidateInvoiceCaches(invoiceId);

        invoice.getInvoiceDiscounts().add(invoiceDiscount);
        Map<String, Object> payload = invoicePayload(invoice);
        payload.put("discountId", discountId);
        payload.put("discountApplied", discountApplied);
        emitAfterCommit("DISCOUNT_APPLIED", payload);
        return invoice;
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    public Invoice createInvoice(Invoice invoice) {
        Invoice saved = invoiceRepository.save(invoice);
        invalidateInvoiceCaches(null);
        return saved;
    }

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). */
    @Cacheable(cacheNames = "invoice-service::invoice", key = "#id")
    public Invoice getInvoiceById(Long id) {
        return findById(id);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Invoice updateInvoice(Long id, Invoice updatedInvoice) {
        Invoice existing = findById(id);
        existing.setBookingId(updatedInvoice.getBookingId());
        existing.setUserId(updatedInvoice.getUserId());
        existing.setAmount(updatedInvoice.getAmount());
        existing.setMethod(updatedInvoice.getMethod());
        existing.setStatus(updatedInvoice.getStatus());
        existing.setTransactionDetails(updatedInvoice.getTransactionDetails());
        existing.setCreatedAt(updatedInvoice.getCreatedAt());
        Invoice saved = invoiceRepository.save(existing);
        invalidateInvoiceCaches(id);
        return saved;
    }

    public void deleteInvoice(Long id) {
        Invoice invoice = findById(id);
        Map<String, Object> payload = invoicePayload(invoice);
        invoiceRepository.deleteById(id);
        invalidateInvoiceCaches(id);
        emitAfterCommit("INVOICE_DELETED", payload);
    }

    /** S5-F8: invoice details with applied discounts — 15 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "invoice-service::S5-F8", key = "#invoiceId")
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

    /** S5-F1: search invoices by status/date — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "invoice-service::S5-F1",
               key = "T(java.util.Objects).hash(#status, #startDate, #endDate)")
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

        Invoice invoice = findById(invoiceId);

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
        invalidateInvoiceCaches(invoiceId);
        Map<String, Object> payload = invoicePayload(saved);
        payload.put("details", Map.of("refundReason", reason));
        emitAfterCommit("REFUNDED", payload);
        return saved;
    }

    /** S5-F3: user invoice summary — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "invoice-service::S5-F3", key = "#userId")
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

        BigDecimal totalPrice = booking[1] != null
                ? new BigDecimal(booking[1].toString())
                : BigDecimal.ZERO;

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
            invalidateInvoiceCaches(null);
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
        invalidateInvoiceCaches(null);
        emitAfterCommit("COMPLETED", invoicePayload(completed));
        return completed;
    }

    /** S5-F10: revenue by provider specialty — 10 min TTL (§10.5.1). */
    @Cacheable(cacheNames = "invoice-service::S5-F10",
               key = "T(java.util.Objects).hash(#startDate, #endDate)")
    public List<ServiceTypeRevenueDTO> getRevenueByServiceType(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = endDate.atTime(23, 59, 59, 999_000_000);

        List<Object[]> rows = invoiceRepository.getRevenueByServiceType(from, to);
        List<ServiceTypeRevenueDTO> result = new ArrayList<>();
        for (Object[] row : rows) {
            BigDecimal cancellationFeeRevenue = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            BigDecimal netBookingRevenue      = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            long       bookingCount           = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            long       cancelledCount         = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            BigDecimal totalRevenue    = cancellationFeeRevenue.add(netBookingRevenue);
            BigDecimal cancellationRate = bookingCount > 0
                    ? BigDecimal.valueOf(cancelledCount).divide(BigDecimal.valueOf(bookingCount), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(ServiceTypeRevenueDTO.builder()
                    .specialty(row[0] != null ? row[0].toString() : "UNKNOWN")
                    .totalRevenue(totalRevenue)
                    .cancellationFeeRevenue(cancellationFeeRevenue)
                    .netBookingRevenue(netBookingRevenue)
                    .bookingCount(bookingCount)
                    .cancellationRate(cancellationRate)
                    .build());
        }
        return result;
    }

    /** S5-F11: Payment method breakdown — 10 min TTL. */
    @Cacheable(cacheNames = "invoice-service::S5-F11",
               key = "T(java.util.Objects).hash(#startDate, #endDate)")
    public List<PaymentMethodAnalyticsDTO> getPaymentMethodBreakdown(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }
        java.time.LocalDateTime from = startDate.atStartOfDay();
        java.time.LocalDateTime to   = endDate.atTime(23, 59, 59, 999_000_000);

        List<String> actions = java.util.List.of("COMPLETED", "FAILED");
        List<PaymentMethodBreakdown> rows = paymentAuditEventRepository.findMethodBreakdown(from, to, actions);

        // Return empty list if no data exists for the date range (spec §4.4.2)
        if (rows.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        java.util.Map<String, PaymentMethodAnalyticsDTO> map = new java.util.HashMap<>();
        for (PaymentMethodBreakdown row : rows) {
            long success = row.getSuccessCount();
            long failure = row.getFailureCount();
            long denom = success + failure;
            double successRate = denom == 0 ? 0.0 : ((double) success) / ((double) denom);
            java.math.BigDecimal totalAmount = java.math.BigDecimal.valueOf(row.getTotalAmount());
            PaymentMethodAnalyticsDTO dto = new PaymentMethodAnalyticsDTO(row.getMethod(), success, failure, successRate, totalAmount);
            map.put(row.getMethod(), dto);
        }

        // Ensure all methods are present
        for (Invoice.PaymentMethod pm : Invoice.PaymentMethod.values()) {
            if (!map.containsKey(pm.name())) {
                map.put(pm.name(), new PaymentMethodAnalyticsDTO(pm.name(), 0L, 0L, 0.0, java.math.BigDecimal.ZERO));
            }
        }

        return new java.util.ArrayList<>(map.values());
    }

    /** Emit ANALYTICS_VIEWED unconditionally — must fire even on cache hits (§4.4.4). */
    public void emitAnalyticsViewed(String featureId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("feature", featureId);
        notifyObservers("ANALYTICS_VIEWED", payload);
    }

    /** S5-F6: revenue report — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "invoice-service::S5-F6",
               key = "T(java.util.Objects).hash(#startDate, #endDate)")
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
        Invoice invoice = findById(id);

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

        Invoice saved = invoiceRepository.save(invoice);
        invalidateInvoiceCaches(id);
        Map<String, Object> payload = invoicePayload(saved);
        payload.put("retryCount", retryCount);
        emitAfterCommit("RETRY_ATTEMPTED", payload);
        return saved;
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    private AppliedDiscountDTO mapAppliedDiscount(InvoiceDiscount invoiceDiscount) {
        Discount discount = invoiceDiscount.getDiscount();
        return new AppliedDiscountDTO(
                discount != null ? discount.getCode() : null,
                discount != null ? discount.getDiscountType() : null,
                invoiceDiscount.getDiscountApplied(),
                invoiceDiscount.getAppliedAt()
        );
    }

    /** Non-cached DB fetch used by all write paths (§4.4.4). */
    Invoice findById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
    }

    /** Invalidate entity detail + all feature caches on any invoice write (§4.4.4). */
    private void invalidateInvoiceCaches(Long id) {
        if (id != null) {
            cacheInvalidator.deleteKey("invoice-service::invoice::" + id);
            cacheInvalidator.deleteKey("invoice-service::S5-F8::" + id);
        }
        cacheInvalidator.wildcardDelete("invoice-service::S5-F1::*");
        cacheInvalidator.wildcardDelete("invoice-service::S5-F3::*");
        cacheInvalidator.wildcardDelete("invoice-service::S5-F6::*");
        cacheInvalidator.wildcardDelete("invoice-service::S5-F9::*");
        cacheInvalidator.wildcardDelete("invoice-service::S5-F10::*");
        cacheInvalidator.wildcardDelete("invoice-service::S5-F11::*");
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
