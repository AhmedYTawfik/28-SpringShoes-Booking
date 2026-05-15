package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
import com.team28.booking.contracts.dto.ProviderBookingSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {
    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getActiveBookingCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/completed-count")
    long getCompletedBookingCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/provider/{providerId}/summary")
    ProviderBookingSummaryDTO getProviderBookingSummary(
            @PathVariable("providerId") Long providerId,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate
    );

    @GetMapping("/api/bookings/provider/{providerId}/active-count")
    int getProviderActiveCount(@PathVariable("providerId") Long providerId);

    @GetMapping("/api/bookings/provider/{providerId}/completed-count")
    long getProviderCompletedCount(@PathVariable("providerId") Long providerId);

    @GetMapping("/api/bookings/{bookingId}")
    BookingDTO getBooking(@PathVariable("bookingId") Long bookingId);
}
