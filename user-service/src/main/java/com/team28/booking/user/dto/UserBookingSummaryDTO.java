package com.team28.booking.user.dto;

import java.math.BigDecimal;

public record UserBookingSummaryDTO(
        Long userId,
        String name,
        Long totalBookings,
        Long completedBookings,
        Long cancelledBookings,
        BigDecimal totalSpent,
        BigDecimal averageBookingPrice
) {
    public static UserBookingSummaryDTOBuilder builder() { return new UserBookingSummaryDTOBuilder(); }
}
