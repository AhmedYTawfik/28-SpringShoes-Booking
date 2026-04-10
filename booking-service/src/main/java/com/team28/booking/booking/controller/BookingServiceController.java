package com.team28.booking.booking.controller;

import com.team28.booking.booking.model.BookingService;
import com.team28.booking.booking.service.BookingItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking-services")
public class BookingServiceController {

    private final BookingItemService bookingItemService;

    public BookingServiceController(BookingItemService bookingItemService) {
        this.bookingItemService = bookingItemService;
    }

    @PostMapping
    public ResponseEntity<BookingService> createBookingService(
            @RequestParam Long bookingId,
            @RequestBody BookingService bookingService) {
        try {
            return ResponseEntity.ok(bookingItemService.createBookingService(bookingId, bookingService));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<BookingService>> getAllBookingServices() {
        return ResponseEntity.ok(bookingItemService.getAllBookingServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingService> getBookingServiceById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookingItemService.getBookingServiceById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookingService> updateBookingService(
            @PathVariable Long id,
            @RequestBody BookingService bookingService) {
        try {
            return ResponseEntity.ok(bookingItemService.updateBookingService(id, bookingService));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookingService(@PathVariable Long id) {
        try {
            bookingItemService.deleteBookingService(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
