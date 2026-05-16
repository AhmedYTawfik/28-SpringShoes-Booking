package com.team28.booking.booking.controller;

import com.team28.booking.booking.dto.AddServicesRequestDTO;
import com.team28.booking.booking.dto.BookingAnalyticsDTO;
import com.team28.booking.booking.dto.BookingAnalyticsDashboardDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.BookingEstimateRequestDTO;
import com.team28.booking.booking.dto.ProviderRecommendationDTO;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
import com.team28.booking.contracts.dto.ProviderBookingSummaryDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import java.util.List;
import java.util.Map;

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
  
    @PutMapping("/{id}/assign")
    public ResponseEntity<Booking> assignProvider(
            @PathVariable Long id,
            @RequestParam Long providerId) {
        return ResponseEntity.ok(bookingService.assignProvider(id, providerId));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Booking> completeBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.completeBooking(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Booking> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Booking>> searchBookings(
            @RequestParam(required = false) Booking.Status status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(bookingService.searchBookings(
                status != null ? status.name() : null, startDate, endDate));
    }

    @GetMapping("/analytics")
    public ResponseEntity<BookingAnalyticsDTO> getAnalytics(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(bookingService.getAnalytics(startDate, endDate));
    }

    @GetMapping("/analytics/dashboard")
    public ResponseEntity<BookingAnalyticsDashboardDTO> getDashboardAnalytics(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(bookingService.getDashboardAnalytics(startDate, endDate));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<BookingDetailsDTO> getBookingDetails(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingDetails(id));
    }

    /** S3-F11: Record User-Provider Booking Pattern */
    @PostMapping("/{bookingId}/record-interaction")
    public ResponseEntity<Map<String, Object>> recordInteraction(@PathVariable Long bookingId) {
        bookingService.recordInteraction(bookingId);
        return ResponseEntity.ok(Map.of("message", "Interaction recorded successfully"));
    }

    /** S3-F12: Get provider recommendations for a user (collaborative filtering via Neo4j). */
    @GetMapping("/recommendations")
    public ResponseEntity<List<ProviderRecommendationDTO>> getRecommendations(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "5") int limit) {
        return ResponseEntity.ok(bookingService.getRecommendations(userId, limit));
    }

    /** S3-F7: Add services to a REQUESTED or CONFIRMED booking. */
    @PostMapping("/{id}/services")
    public ResponseEntity<Booking> addServices(
            @PathVariable Long id,
            @RequestBody AddServicesRequestDTO request) {
        return ResponseEntity.ok(bookingService.addServices(id, request));
    }

    /** GET /api/bookings/user/{userId}/summary — consumed by user-service via Feign. */
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<BookingSummaryDTO> getUserBookingSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getUserBookingSummary(userId));
    }

    /** GET /api/bookings/user/{userId}/active-count — consumed by user-service via Feign. */
    @GetMapping("/user/{userId}/active-count")
    public ResponseEntity<Integer> getUserActiveCount(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getUserActiveCount(userId));
    }

    /** GET /api/bookings/user/{userId}/completed-count — consumed by user-service via Feign. */
    @GetMapping("/user/{userId}/completed-count")
    public ResponseEntity<Long> getUserCompletedCount(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getUserCompletedCount(userId));
    }

    /** GET /api/bookings/provider/{providerId}/summary — consumed by provider-service via Feign. */
    @GetMapping("/provider/{providerId}/summary")
    public ResponseEntity<ProviderBookingSummaryDTO> getProviderBookingSummary(
            @PathVariable Long providerId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(bookingService.getProviderBookingSummary(providerId, startDate, endDate));
    }

    /** GET /api/bookings/provider/{providerId}/active-count — consumed by provider-service via Feign. */
    @GetMapping("/provider/{providerId}/active-count")
    public ResponseEntity<Integer> getProviderActiveCount(@PathVariable Long providerId) {
        return ResponseEntity.ok(bookingService.getProviderActiveCount(providerId));
    }

    /** GET /api/bookings/provider/{providerId}/completed-count — consumed by provider-service via Feign. */
    @GetMapping("/provider/{providerId}/completed-count")
    public ResponseEntity<Long> getProviderCompletedCount(@PathVariable Long providerId) {
        return ResponseEntity.ok(bookingService.getProviderCompletedCount(providerId));
    }
}
