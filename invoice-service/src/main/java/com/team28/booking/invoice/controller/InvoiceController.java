package com.team28.booking.invoice.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team28.booking.invoice.dto.DiscountUsageDTO;
import com.team28.booking.invoice.dto.ProcessInvoiceRequest;
import com.team28.booking.invoice.dto.RetryInvoiceRequest;
import com.team28.booking.invoice.dto.RevenueReportDTO;
import com.team28.booking.invoice.model.Invoice;
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

    @GetMapping
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
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

    // ── S5-F4: Process Invoice for Booking ──────────────────────────────────

    @PostMapping("/process")
    public ResponseEntity<Invoice> processInvoiceForBooking(@RequestBody ProcessInvoiceRequest request) {
        return ResponseEntity.status(201).body(invoiceService.processInvoiceForBooking(request));
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
