package com.team28.booking.invoice.controller;

import com.team28.booking.invoice.model.InvoiceDiscount;
import com.team28.booking.invoice.service.InvoiceDiscountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoice-discounts")
public class InvoiceDiscountController {

    private final InvoiceDiscountService invoiceDiscountService;

    public InvoiceDiscountController(InvoiceDiscountService invoiceDiscountService) {
        this.invoiceDiscountService = invoiceDiscountService;
    }

    // Create
    @PostMapping
    public ResponseEntity<InvoiceDiscount> createInvoiceDiscount(@RequestBody InvoiceDiscount invoiceDiscount) {
        return ResponseEntity.status(201).body(invoiceDiscountService.createInvoiceDiscount(invoiceDiscount));
    }

    // Read by ID
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceDiscount> getInvoiceDiscountById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceDiscountService.getInvoiceDiscountById(id));
    }

    // Read all
    @GetMapping
    public ResponseEntity<List<InvoiceDiscount>> getAllInvoiceDiscounts() {
        return ResponseEntity.ok(invoiceDiscountService.getAllInvoiceDiscounts());
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<InvoiceDiscount> updateInvoiceDiscount(@PathVariable Long id, @RequestBody InvoiceDiscount invoiceDiscount) {
        return ResponseEntity.ok(invoiceDiscountService.updateInvoiceDiscount(id, invoiceDiscount));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvoiceDiscount(@PathVariable Long id) {
        invoiceDiscountService.deleteInvoiceDiscount(id);
        return ResponseEntity.noContent().build();
    }
}