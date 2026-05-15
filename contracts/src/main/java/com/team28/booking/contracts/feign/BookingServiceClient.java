package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
import com.team28.booking.contracts.dto.ProviderBookingSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {
    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getActiveBookingCount(@PathVariable Long userId);

    @GetMapping("/api/bookings/user/{userId}/completed-count")
    long getCompletedBookingCount(@PathVariable Long userId);

    @GetMapping("/api/bookings/provider/{providerId}/summary")
    ProviderBookingSummaryDTO getProviderBookingSummary(
            @PathVariable Long providerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    );

    @GetMapping("/api/bookings/provider/{providerId}/active-count")
    int getProviderActiveCount(@PathVariable Long providerId);

    @GetMapping("/api/bookings/provider/{providerId}/completed-count")
    long getProviderCompletedCount(@PathVariable Long providerId);

    @GetMapping("/api/bookings/{bookingId}")
    BookingDTO getBooking(@PathVariable Long bookingId);
}
