package com.team28.booking.invoice.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team28.booking.contracts.dto.InvoiceAmountDTO;
import com.team28.booking.contracts.dto.InvoiceAmountsRequest;
import com.team28.booking.invoice.dto.CancellationRefundRequest;
import com.team28.booking.invoice.dto.DiscountUsageDTO;
import com.team28.booking.invoice.dto.InvoiceDetailsDTO;
import com.team28.booking.invoice.dto.ProcessInvoiceRequest;
import com.team28.booking.invoice.dto.RefundRequest;
import com.team28.booking.invoice.dto.RetryInvoiceRequest;
import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.dto.ServiceTypeRevenueDTO;
import com.team28.booking.invoice.dto.PaymentMethodAnalyticsDTO;
import com.team28.booking.invoice.dto.UserInvoiceSummaryDTO;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.model.Invoice.InvoiceStatus;
import com.team28.booking.invoice.service.InvoiceService;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Invoice> createInvoice(@RequestBody Invoice invoice) {
        return ResponseEntity.status(201).body(invoiceService.createInvoice(invoice));
    }
    @GetMapping("/discounts/top-used")
    public ResponseEntity<List<DiscountUsageDTO>> getTopUsedDiscounts( @RequestParam(name = "limit", defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(invoiceService.getTopUsedDiscountsReport(limit)); 
    }

    @GetMapping("/{id}")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @GetMapping("/{invoiceId}/details")
    public ResponseEntity<InvoiceDetailsDTO> getInvoiceDetails(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(invoiceService.getInvoiceDetails(invoiceId));
    }

    @PostMapping("/{invoiceId}/discounts/{discountId}")
    public ResponseEntity<Invoice> applyDiscountToInvoice(@PathVariable Long invoiceId, @PathVariable Long discountId) {
        return ResponseEntity.ok(invoiceService.applyDiscountToInvoice(invoiceId, discountId));
    }

    @GetMapping
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    // Search invoices by status and date range
    // Supports partial matching - returns empty list if no matches
    @GetMapping("/search")
    public ResponseEntity<List<Invoice>> searchInvoices(
        @RequestParam(required = false) InvoiceStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(invoiceService.searchInvoices(status, startDate, endDate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Invoice> updateInvoice(@PathVariable Long id, @RequestBody Invoice invoice) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, invoice));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Invoice> processRefund(
        @PathVariable Long id,
        @RequestBody RefundRequest refundRequest
    ) {
        return ResponseEntity.ok(invoiceService.processRefund(id, refundRequest.reason()));
    }

    // ── S5-F12: Process Cancellation Refund with Timing Handling ────────────

    @PostMapping("/{id}/refund-cancellation")
    public ResponseEntity<Invoice> processCancellationRefund(
            @PathVariable Long id,
            @RequestBody CancellationRefundRequest request) {
        return ResponseEntity.ok(invoiceService.processCancellationRefund(id, request));
    }

    // Get User Invoice Summary
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserInvoiceSummaryDTO> getUserInvoiceSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(invoiceService.getUserInvoiceSummary(userId));
    }

    // ── S5-ENDPOINTS: Feign-callable endpoints ───────────────────────────────

    @GetMapping("/user/{userId}/total")
    public ResponseEntity<BigDecimal> getUserTotalAmount(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(invoiceService.getUserTotalAmount(userId, startDate, endDate));
    }

    @PostMapping("/by-bookings")
    public ResponseEntity<Map<Long, InvoiceAmountDTO>> getInvoicesByBookingIds(
            @RequestBody InvoiceAmountsRequest request) {
        return ResponseEntity.ok(invoiceService.getInvoicesByBookingIds(request.bookingIds()));
    }

    // ── S5-F4: Process Invoice for Booking (saga-aware) ─────────────────────

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<Invoice> processInvoiceByBookingPath(
            @PathVariable Long bookingId,
            @RequestBody ProcessInvoiceRequest request,
            @RequestParam(defaultValue = "false") boolean simulateFailure) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long callerUserId = extractUserId(auth);
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Invoice invoice = invoiceService.processInvoiceForBooking(
                bookingId, request.getMethod(), request.getCardLastFour(),
                callerUserId, isAdmin, simulateFailure);
        if (simulateFailure) {
            return ResponseEntity.badRequest().body(invoice);
        }
        return ResponseEntity.status(201).body(invoice);
    }

    @SuppressWarnings("unchecked")
    private Long extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Map) {
            Object id = ((Map<String, Object>) principal).get("id");
            return id != null ? ((Number) id).longValue() : null;
        }
        return null;
    }

    // ── S5-F10: Revenue by Service Type with Cancellation Fee Breakdown ─────

    @GetMapping("/analytics/service-type")
    public ResponseEntity<List<ServiceTypeRevenueDTO>> getRevenueByServiceType(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ServiceTypeRevenueDTO> result = invoiceService.getRevenueByServiceType(startDate, endDate);
        invoiceService.emitAnalyticsViewed("S5-F10");
        return ResponseEntity.ok(result);
    }

    // ── S5-F11: Payment Method Breakdown ─────────────────────────────────
    @GetMapping("/analytics/methods")
    public ResponseEntity<List<PaymentMethodAnalyticsDTO>> getPaymentMethodBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<PaymentMethodAnalyticsDTO> result = invoiceService.getPaymentMethodBreakdown(startDate, endDate);
        invoiceService.emitAnalyticsViewed("S5-F11");
        return ResponseEntity.ok(result);
    }

    // ── S5-F6: Revenue Report by Date Range ─────────────────────────────────

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(invoiceService.getRevenueReport(startDate, endDate));
    }

    // ── S5-F7: Retry Failed Invoice ──────────────────────────────────────────

    @PutMapping("/{id}/retry")
    public ResponseEntity<Invoice> retryFailedInvoice(@PathVariable Long id,
                                                       @RequestBody(required = false) RetryInvoiceRequest request) {
        if (request == null) request = new RetryInvoiceRequest();
        return ResponseEntity.ok(invoiceService.retryFailedInvoice(id, request));
    }
}
