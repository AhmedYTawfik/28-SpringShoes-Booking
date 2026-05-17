package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.BookingDTO;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
import com.team28.booking.contracts.dto.ProviderBookingSummaryDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class BookingServiceClientFallback implements BookingServiceClient {
    @Override
    public BookingSummaryDTO getUserBookingSummary(Long userId) {
        return emptyUserSummary();
    }

    @Override
    public BookingSummaryDTO getUserBookingSummary(Long userId, String startDate, String endDate) {
        return emptyUserSummary();
    }

    @Override
    public int getActiveBookingCount(Long userId) {
        return 0;
    }

    @Override
    public long getCompletedBookingCount(Long userId) {
        return 0;
    }

    @Override
    public ProviderBookingSummaryDTO getProviderBookingSummary(Long providerId, String startDate, String endDate) {
        return new ProviderBookingSummaryDTO(0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public int getProviderActiveCount(Long providerId) {
        return 0;
    }

    @Override
    public long getProviderCompletedCount(Long providerId) {
        return 0;
    }

    @Override
    public BookingDTO getBooking(Long bookingId) {
        return new BookingDTO(bookingId, null, null, "UNAVAILABLE", BigDecimal.ZERO, null, null, null, null, Map.of("fallback", true));
    }

    private BookingSummaryDTO emptyUserSummary() {
        return new BookingSummaryDTO(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
