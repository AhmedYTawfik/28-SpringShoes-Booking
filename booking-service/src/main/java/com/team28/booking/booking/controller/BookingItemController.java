package com.team28.booking.booking.controller;

import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.service.BookingItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking-services")
public class BookingItemController {

    private final BookingItemService bookingItemService;

    public BookingItemController(BookingItemService bookingItemService) {
        this.bookingItemService = bookingItemService;
    }

    @PostMapping
    public ResponseEntity<BookingItem> createBookingItem(
            @RequestParam Long bookingId,
            @RequestBody BookingItem bookingItem) {
        return ResponseEntity.ok(bookingItemService.createBookingItem(bookingId, bookingItem));
    }

    @GetMapping
    public ResponseEntity<List<BookingItem>> getAllBookingItems() {
        return ResponseEntity.ok(bookingItemService.getAllBookingItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingItem> getBookingItemById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingItemService.getBookingItemById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookingItem> updateBookingItem(
            @PathVariable Long id,
            @RequestBody BookingItem bookingItem) {
        return ResponseEntity.ok(bookingItemService.updateBookingItem(id, bookingItem));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookingItem(@PathVariable Long id) {
        bookingItemService.deleteBookingItem(id);
        return ResponseEntity.noContent().build();
    }
}
