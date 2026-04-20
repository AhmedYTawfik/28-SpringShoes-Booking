package com.team28.booking.booking.controller;

import com.team28.booking.booking.dto.AddServiceItemDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.BookingEstimateRequestDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody Booking booking) {
        return ResponseEntity.ok(bookingService.createBooking(booking));
    }

    @GetMapping
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Booking> updateBooking(@PathVariable Long id, @RequestBody Booking booking) {
        return ResponseEntity.ok(bookingService.updateBooking(id, booking));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(@PathVariable Long id) {
        bookingService.deleteBooking(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/estimate")
    public ResponseEntity<BookingEstimateDTO> getEstimate(@RequestBody BookingEstimateRequestDTO request) {
        return ResponseEntity.ok(bookingService.getEstimate(request));
    }

    @GetMapping("/metadata/search")
    public ResponseEntity<List<Booking>> searchByMetadata(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(bookingService.searchByMetadata(key, value));
    }
  
    @PutMapping("/{id}/complete")
    public ResponseEntity<Booking> completeBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.completeBooking(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Booking> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @PostMapping("/{id}/services")
    public ResponseEntity<Booking> addServices(
            @PathVariable Long id,
            @RequestBody List<AddServiceItemDTO> services) {
        return ResponseEntity.ok(bookingService.addServicesToBooking(id, services));
    }
  
    @GetMapping("/{bookingId}/details")
    public ResponseEntity<BookingDetailsDTO> getBookingDetails(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBookingDetails(bookingId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Booking>> searchBookings(
            @RequestParam(required = false) String status,
            @RequestParam java.time.LocalDate startDate,
            @RequestParam java.time.LocalDate endDate) {
        return ResponseEntity.ok(bookingService.searchBookings(status, startDate, endDate));
    }
}
