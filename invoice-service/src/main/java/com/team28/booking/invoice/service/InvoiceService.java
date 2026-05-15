package com.team28.booking.invoice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.dto.ProviderDTO;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.feign.ProviderServiceClient;
import com.team28.booking.invoice.adapter.ObjectArrayDtoAdapter;
import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.dto.AppliedDiscountDTO;
import com.team28.booking.invoice.dto.CancellationRefundRequest;
import com.team28.booking.invoice.dto.DiscountUsageDTO;
import com.team28.booking.invoice.dto.InvoiceDetailsDTO;
import com.team28.booking.invoice.dto.PaymentMethodAnalyticsDTO;
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
import com.team28.booking.invoice.messaging.PaymentEventPublisher;
import com.team28.booking.invoice.mongo.PaymentAuditEventRepository;
import com.team28.booking.invoice.mongo.PaymentMethodBreakdown;
import com.team28.booking.invoice.observer.MongoEventLogger;
import com.team28.booking.invoice.observer.Observable;
import com.team28.booking.invoice.repository.DiscountRepository;
import com.team28.booking.invoice.repository.DiscountUsageProjection;
import com.team28.booking.invoice.repository.InvoiceDiscountRepository;
import com.team28.booking.invoice.repository.InvoiceRepository;
import com.team28.booking.invoice.strategy.NoRefundStrategy;
import com.team28.booking.invoice.strategy.RefundResult;
import com.team28.booking.invoice.strategy.RefundStrategy;
import com.team28.booking.invoice.strategy.RefundStrategySelector;
import com.team28.booking.invoice.exception.ConflictException;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.feign.UserServiceClient;

import feign.FeignException;
import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InvoiceService extends Observable {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final DiscountRepository discountRepository;
    private final InvoiceDiscountRepository invoiceDiscountRepository;
    private final MongoEventLogger mongoEventLogger;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;
    private final CacheInvalidator cacheInvalidator;
    private final RefundStrategySelector refundStrategySelector;
    private final PaymentAuditEventRepository paymentAuditEventRepository;
    private final PaymentEventPublisher eventPublisher;
    private final BookingServiceClient bookingServiceClient;
    private final InvoiceStatusLockService invoiceStatusLockService;
    private final ProviderServiceClient providerServiceClient;
    private final UserServiceClient userServiceClient;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          DiscountRepository discountRepository,
                          InvoiceDiscountRepository invoiceDiscountRepository,
                          MongoEventLogger mongoEventLogger,
                          ObjectArrayDtoAdapter objectArrayDtoAdapter,
                          CacheInvalidator cacheInvalidator,
                          RefundStrategySelector refundStrategySelector,
                          PaymentAuditEventRepository paymentAuditEventRepository,
                          PaymentEventPublisher eventPublisher,
                          BookingServiceClient bookingServiceClient,
                          ProviderServiceClient providerServiceClient,
                          UserServiceClient userServiceClient,
                          InvoiceStatusLockService invoiceStatusLockService) {
        this.invoiceRepository = invoiceRepository;
        this.discountRepository = discountRepository;
        this.invoiceDiscountRepository = invoiceDiscountRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;
        this.cacheInvalidator = cacheInvalidator;
        this.refundStrategySelector = refundStrategySelector;
        this.paymentAuditEventRepository = paymentAuditEventRepository;
        this.eventPublisher = eventPublisher;
        this.bookingServiceClient = bookingServiceClient;
        this.invoiceStatusLockService = invoiceStatusLockService;
        this.providerServiceClient = providerServiceClient;
        this.userServiceClient = userServiceClient;
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
    public List<Invoice> searchInvoices(InvoiceStatus status, LocalDate start, LocalDate end) {
        LocalDateTime startDate = start != null ? start.atStartOfDay() : null;
        LocalDateTime endDate = end != null ? end.atTime(LocalTime.MAX) : null;
        
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
        double refundAmt = saved.getAmount() != null ? saved.getAmount().doubleValue() : 0.0;
        publishAfterCommit(() -> eventPublisher.publishPaymentRefunded(
                saved.getId(), saved.getBookingId(), refundAmt));
        return saved;
    }

    // ── S5-ENDPOINTS: New Feign-callable Endpoints ───────────────────────────

    /** S5-ENDPOINTS: total COMPLETED invoice amount for a user in a date range. */
    public java.math.BigDecimal getUserTotalAmount(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime from = startDate != null ? startDate.atStartOfDay() : LocalDateTime.MIN;
        LocalDateTime to   = endDate   != null ? endDate.atTime(LocalTime.MAX) : LocalDateTime.MAX;
        java.math.BigDecimal total = invoiceRepository.sumCompletedAmountByUserAndDateRange(userId, from, to);
        return total != null ? total : java.math.BigDecimal.ZERO;
    }

    /** S5-ENDPOINTS: batch fetch COMPLETED invoices keyed by bookingId; omits missing. */
    public Map<Long, com.team28.booking.contracts.dto.InvoiceAmountDTO> getInvoicesByBookingIds(
            List<Long> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Map.of();
        }
        List<Invoice> invoices = invoiceRepository.findCompletedByBookingIds(bookingIds);
        Map<Long, com.team28.booking.contracts.dto.InvoiceAmountDTO> result = new java.util.LinkedHashMap<>();
        for (Invoice inv : invoices) {
            result.put(inv.getBookingId(),
                    new com.team28.booking.contracts.dto.InvoiceAmountDTO(inv.getBookingId(), inv.getAmount()));
        }
        return result;
    }

    /** S5-F3: user invoice summary — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "invoice-service::S5-F3", key = "#userId")
    public UserInvoiceSummaryDTO getUserInvoiceSummary(Long userId) {
        log.info("S5-F3 getUserInvoiceSummary: Feign GET /api/users/{} — before", userId);
        try {
            userServiceClient.getUser(userId);
            log.info("S5-F3 getUserInvoiceSummary: Feign GET /api/users/{} — after", userId);
        } catch (FeignException.NotFound e) {
            log.warn("S5-F3 getUserInvoiceSummary: user {} not found via Feign", userId);
            throw new ResourceNotFoundException("User not found with id: " + userId);
        } catch (FeignException e) {
            log.error("S5-F3 getUserInvoiceSummary: Feign exception for userId={}: {}", userId, e.getMessage());
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        List<Object[]> results = invoiceRepository.getInvoiceSummaryByUserId(userId);
        return objectArrayDtoAdapter.toUserInvoiceSummaryDTO(userId, results);
    }

    // ── S5-F4: Process Invoice for Booking (saga-aware) ─────────────────────

    /**
     * Saga-aware payment flow:
     * 1. SELECT FOR UPDATE on PENDING invoice (commits in own tx via InvoiceStatusLockService)
     * 2. Feign-confirm booking is PAYMENT_PENDING; revert to PENDING + 400 if not
     * 3. Mock payment; on success → COMPLETED + payment.completed; on failure → FAILED + payment.failed
     */
    @Transactional
    public Invoice processInvoiceForBooking(Long bookingId, String method, String cardLastFour,
                                            Long callerUserId, boolean isAdmin, boolean simulateFailure) {
        log.info("S5-F4 processInvoiceForBooking: bookingId={} callerUserId={}", bookingId, callerUserId);

        // Step 1: lock + set PROCESSING in its own committed transaction
        Invoice invoice = invoiceStatusLockService.lockAndSetProcessing(bookingId);
        log.info("S5-F4 invoice {} set to PROCESSING for bookingId={}", invoice.getId(), bookingId);

        // Step 2: auth check — caller must be booking owner or ADMIN
        log.info("S5-F4 Feign GET /api/bookings/{} — before", bookingId);
        BookingDTO booking;
        try {
            booking = bookingServiceClient.getBooking(bookingId);
            log.info("S5-F4 Feign GET /api/bookings/{} — after, status={}", bookingId, booking.status());
        } catch (FeignException e) {
            log.error("S5-F4 Feign GET /api/bookings/{} — exception: {}", bookingId, e.getMessage());
            invoiceStatusLockService.revertToPending(invoice.getId());
            throw new BadRequestException("booking-service unavailable: " + e.getMessage());
        }

        if (!isAdmin && !booking.userId().equals(callerUserId)) {
            invoiceStatusLockService.revertToPending(invoice.getId());
            throw new ConflictException("Forbidden: caller is not the booking owner");
        }

        // Step 3: confirm booking is PAYMENT_PENDING
        if (!"PAYMENT_PENDING".equals(booking.status())) {
            invoiceStatusLockService.revertToPending(invoice.getId());
            throw new BadRequestException(
                    "Booking must be PAYMENT_PENDING to process payment but was: " + booking.status());
        }

        // Step 4: update method and transactionDetails, then attempt payment
        Invoice.PaymentMethod parsedMethod = parsePaymentMethod(method);
        invoice.setMethod(parsedMethod);

        Map<String, Object> details = invoice.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("gateway", "internal");
        details.put("gatewayResponse", "approved");
        details.put("cardLastFour", cardLastFour);
        details.put("cancellationFee", 0);

        if (simulateFailure) {
            invoice.setStatus(Invoice.InvoiceStatus.FAILED);
            details.put("failedAt", LocalDateTime.now().toString());
            details.put("reason", "simulated_gateway_failure");
            invoice.setTransactionDetails(details);
            Invoice failed = invoiceRepository.save(invoice);
            invalidateInvoiceCaches(failed.getId());
            log.info("S5-F4 payment FAILED for invoiceId={} bookingId={}", failed.getId(), bookingId);
            eventPublisher.publishPaymentFailed(failed.getId(), bookingId, "simulated_gateway_failure");
            return failed;
        }

        invoice.setStatus(Invoice.InvoiceStatus.COMPLETED);
        details.put("completedAt", LocalDateTime.now().toString());
        invoice.setTransactionDetails(details);
        Invoice completed = invoiceRepository.save(invoice);
        invalidateInvoiceCaches(completed.getId());
        log.info("S5-F4 payment COMPLETED for invoiceId={} bookingId={}", completed.getId(), bookingId);
        double amt = completed.getAmount() != null ? completed.getAmount().doubleValue() : 0.0;
        eventPublisher.publishPaymentCompleted(completed.getId(), bookingId, amt);
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
        LocalDateTime to   = endDate.atTime(LocalTime.MAX);

        long t0 = System.currentTimeMillis();

        List<Invoice> invoices = invoiceRepository.findCompletedOrRefundedInRange(from, to);

        // Round 1: fetch each booking once (de-duplicated by bookingId)
        Map<Long, BookingDTO> bookingCache = new HashMap<>();
        for (Invoice inv : invoices) {
            bookingCache.computeIfAbsent(inv.getBookingId(), id -> {
                try { return bookingServiceClient.getBooking(id); }
                catch (Exception e) { return null; }
            });
        }

        // Round 2: fetch each provider once (de-duplicated by providerId)
        Map<Long, String> specialtyCache = new HashMap<>();
        for (BookingDTO booking : bookingCache.values()) {
            if (booking == null) continue;
            specialtyCache.computeIfAbsent(booking.providerId(), pid -> {
                try {
                    ProviderDTO p = providerServiceClient.getProvider(pid);
                    return p != null ? p.specialty() : "UNKNOWN";
                } catch (Exception e) { return "UNKNOWN"; }
            });
        }

        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed > 1000) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                .warn("S5-F10 getRevenueByServiceType took {}ms — {} invoices, {} bookings, {} providers",
                      elapsed, invoices.size(), bookingCache.size(), specialtyCache.size());
        }

        // Java-side aggregation per specialty
        Map<String, java.util.Set<Long>> bookingIdsPerSpecialty = new HashMap<>();
        Map<String, java.util.Set<Long>> cancelledIdsPerSpecialty = new HashMap<>();
        Map<String, BigDecimal> cancFee = new HashMap<>();
        Map<String, BigDecimal> netRev  = new HashMap<>();

        for (Invoice inv : invoices) {
            BookingDTO booking = bookingCache.get(inv.getBookingId());
            String specialty = booking != null
                    ? specialtyCache.getOrDefault(booking.providerId(), "UNKNOWN")
                    : "UNKNOWN";

            boolean cancelled = booking != null && "CANCELLED".equals(booking.status());

            Map<String, Object> txDetails = inv.getTransactionDetails();
            BigDecimal fee = BigDecimal.ZERO;
            if (txDetails != null) {
                Object rawFee = txDetails.get("cancellationFee");
                if (rawFee != null) {
                    try { fee = new BigDecimal(rawFee.toString()); } catch (Exception ignored) { }
                }
            }

            BigDecimal net;
            if (inv.getStatus() == InvoiceStatus.REFUNDED) {
                BigDecimal refund = BigDecimal.ZERO;
                if (txDetails != null) {
                    Object rawRefund = txDetails.get("refundAmount");
                    if (rawRefund != null) {
                        try { refund = new BigDecimal(rawRefund.toString()); } catch (Exception ignored) { }
                    }
                }
                net = inv.getAmount() != null ? inv.getAmount().subtract(refund) : BigDecimal.ZERO;
            } else {
                net = inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
            }

            bookingIdsPerSpecialty.computeIfAbsent(specialty, k -> new java.util.HashSet<>()).add(inv.getBookingId());
            if (cancelled) {
                cancelledIdsPerSpecialty.computeIfAbsent(specialty, k -> new java.util.HashSet<>()).add(inv.getBookingId());
            }

            cancFee.merge(specialty, fee, BigDecimal::add);
            netRev.merge(specialty, net, BigDecimal::add);
        }

        List<ServiceTypeRevenueDTO> result = new ArrayList<>();
        for (String specialty : bookingIdsPerSpecialty.keySet()) {
            long bookingCount   = bookingIdsPerSpecialty.getOrDefault(specialty, java.util.Set.of()).size();
            long cancelledCount = cancelledIdsPerSpecialty.getOrDefault(specialty, java.util.Set.of()).size();
            BigDecimal cf       = cancFee.getOrDefault(specialty, BigDecimal.ZERO);
            BigDecimal nr       = netRev.getOrDefault(specialty, BigDecimal.ZERO);
            BigDecimal total    = cf.add(nr);
            BigDecimal cancellationRate = bookingCount > 0
                    ? BigDecimal.valueOf(cancelledCount).divide(BigDecimal.valueOf(bookingCount), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(ServiceTypeRevenueDTO.builder()
                    .specialty(specialty)
                    .totalRevenue(total)
                    .cancellationFeeRevenue(cf)
                    .netBookingRevenue(nr)
                    .bookingCount(bookingCount)
                    .cancellationRate(cancellationRate)
                    .build());
        }

        result.sort((a, b) -> b.totalRevenue().compareTo(a.totalRevenue()));
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
        java.time.LocalDateTime to   = endDate.atTime(LocalTime.MAX);

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
        LocalDateTime to   = endDate.atTime(LocalTime.MAX);

        Object[] row = (Object[])(invoiceRepository.getRevenueStats(from, to)[0]);
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
            invoice.setMethod(parsePaymentMethod(request.getMethod()));
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
        double retryAmt = saved.getAmount() != null ? saved.getAmount().doubleValue() : 0.0;
        publishAfterCommit(() -> eventPublisher.publishPaymentCompleted(
                saved.getId(), saved.getBookingId(), retryAmt));
        return saved;
    }

    // ── S5-F12: Process Cancellation Refund with Timing Handling ────────────

    @Transactional
    public Invoice processCancellationRefund(Long invoiceId, CancellationRefundRequest request) {
        // --- validate reason ---
        String reason = request.reason();
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Refund reason must not be blank");
        }

        // --- find invoice, validate COMPLETED ---
        Invoice invoice = findById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            throw new BadRequestException(
                "Invoice must be COMPLETED to process cancellation refund");
        }

        // --- Feign booking lookup (replaces cross-service SQL) ---
        log.info("S5-F12 processCancellationRefund: Feign GET /api/bookings/{} — before", invoice.getBookingId());
        BookingDTO booking;
        try {
            booking = bookingServiceClient.getBooking(invoice.getBookingId());
            log.info("S5-F12 processCancellationRefund: Feign GET /api/bookings/{} — after, status={}",
                    invoice.getBookingId(), booking.status());
        } catch (FeignException.NotFound e) {
            log.warn("S5-F12 processCancellationRefund: booking {} not found via Feign", invoice.getBookingId());
            throw new ResourceNotFoundException("Booking not found for invoice: " + invoiceId);
        } catch (FeignException e) {
            log.error("S5-F12 processCancellationRefund: Feign exception for bookingId={}: {}",
                    invoice.getBookingId(), e.getMessage());
            throw new BadRequestException("booking-service unavailable: " + e.getMessage());
        }

        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("status", booking.status());
        bookingData.put("appointmentDate", booking.appointmentDate());

        // --- delegate strategy selection ---
        RefundStrategy strategy = refundStrategySelector.select(bookingData);

        // --- denial path (NoRefundStrategy) ---
        if (strategy instanceof NoRefundStrategy) {
            RefundResult result = strategy.calculateRefund(invoice.getAmount(), bookingData, reason);

            Map<String, Object> denialPayload = invoicePayload(invoice);
            denialPayload.put("strategyName", strategy.strategyName());
            denialPayload.put("denialReason", result.getReasonCode());
            notifyObservers("REFUND_DENIED", denialPayload);

            cacheInvalidator.wildcardDelete("invoice-service::S5-F10::*");
            cacheInvalidator.wildcardDelete("invoice-service::S5-F11::*");

            throw new BadRequestException(result.getReasonCode());
        }

        // --- calculate refund ---
        RefundResult result = strategy.calculateRefund(invoice.getAmount(), bookingData, reason);

        invoice.setStatus(InvoiceStatus.REFUNDED);

        // --- record in transactionDetails JSONB ---
        Map<String, Object> details = invoice.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundAmount", result.getRefundAmount());
        details.put("cancellationFee", result.getCancellationFee());
        details.put("refundReason", reason);
        details.put("strategyName", strategy.strategyName());
        details.put("refundedAt", LocalDateTime.now().toString());
        invoice.setTransactionDetails(details);

        Invoice saved = invoiceRepository.save(invoice);

        // --- invalidate caches ---
        invalidateInvoiceCaches(invoiceId);

        // --- emit REFUNDED + publish payment.refunded (booking-service consumer will flip booking) ---
        Map<String, Object> payload = invoicePayload(saved);
        payload.put("strategyName", strategy.strategyName());
        payload.put("refundReason", reason);
        payload.put("refundAmount", result.getRefundAmount());
        payload.put("cancellationFee", result.getCancellationFee());
        payload.put("details", Map.of(
            "strategyName", strategy.strategyName(),
            "refundReason", reason,
            "refundAmount", result.getRefundAmount(),
            "cancellationFee", result.getCancellationFee()
        ));
        emitAfterCommit("REFUNDED", payload);
        double cancelRefundAmt = result.getRefundAmount() != null ? result.getRefundAmount().doubleValue() : 0.0;
        publishAfterCommit(() -> eventPublisher.publishPaymentRefunded(
                saved.getId(), saved.getBookingId(), cancelRefundAmt));

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

    private void publishAfterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
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

    private Invoice.PaymentMethod parsePaymentMethod(String rawMethod) {
        if (rawMethod == null || rawMethod.isBlank()) {
            throw new BadRequestException("Invalid payment method: " + rawMethod);
        }

        try {
            return Invoice.PaymentMethod.valueOf(rawMethod);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid payment method: " + rawMethod);
        }
    }
}
