package com.team28.booking.booking.controller;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingService;
import com.team28.booking.booking.service.BookingManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking-services")
public class BookingServiceController {

    @Autowired
    private BookingManagementService bookingManagementService;

    @PostMapping
    public ResponseEntity<BookingService> createBookingService(
            @RequestParam Long bookingId,
            @RequestBody BookingService bookingService) {
        Booking booking = bookingManagementService.findBookingById(bookingId);
        if (booking == null) return ResponseEntity.notFound().build();
        bookingService.setBooking(booking);
        BookingService saved = bookingManagementService.createBookingService(bookingService);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<BookingService>> getAllBookingServices() {
        return ResponseEntity.ok(bookingManagementService.findAllBookingServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingService> getBookingServiceById(@PathVariable Long id) {
        BookingService bs = bookingManagementService.findBookingServiceById(id);
        if (bs == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(bs);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookingService> updateBookingService(
            @PathVariable Long id,
            @RequestBody BookingService bookingService) {
        BookingService updated = bookingManagementService.updateBookingService(id, bookingService);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookingService(@PathVariable Long id) {
        if (!bookingManagementService.deleteBookingService(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
