package com.team28.booking.invoice.controller;

import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.service.DiscountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/discounts")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    // Create
    @PostMapping
    public ResponseEntity<Discount> createDiscount(@RequestBody Discount discount) {
        return ResponseEntity.status(201).body(discountService.createDiscount(discount));
    }

    // Read by ID
    @GetMapping("/{id}")
    public ResponseEntity<Discount> getDiscountById(@PathVariable Long id) {
        return ResponseEntity.ok(discountService.getDiscountById(id));
    }

    // Read all
    @GetMapping
    public ResponseEntity<List<Discount>> getAllDiscounts() {
        return ResponseEntity.ok(discountService.getAllDiscounts());
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<Discount> updateDiscount(@PathVariable Long id, @RequestBody Discount discount) {
        return ResponseEntity.ok(discountService.updateDiscount(id, discount));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDiscount(@PathVariable Long id) {
        discountService.deleteDiscount(id);
        return ResponseEntity.noContent().build();
    }
}