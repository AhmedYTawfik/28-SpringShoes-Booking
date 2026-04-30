package com.team28.booking.user.dto;

import java.math.BigDecimal;

public class UserBookingSummaryDTOBuilder {
    private Long userId;
    private String name;
    private Long totalBookings;
    private Long completedBookings;
    private Long cancelledBookings;
    private BigDecimal totalSpent;
    private BigDecimal averageBookingPrice;

    public UserBookingSummaryDTOBuilder userId(Long userId) { this.userId = userId; return this; }
    public UserBookingSummaryDTOBuilder name(String name) { this.name = name; return this; }
    public UserBookingSummaryDTOBuilder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
    public UserBookingSummaryDTOBuilder completedBookings(Long completedBookings) { this.completedBookings = completedBookings; return this; }
    public UserBookingSummaryDTOBuilder cancelledBookings(Long cancelledBookings) { this.cancelledBookings = cancelledBookings; return this; }
    public UserBookingSummaryDTOBuilder totalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; return this; }
    public UserBookingSummaryDTOBuilder averageBookingPrice(BigDecimal averageBookingPrice) { this.averageBookingPrice = averageBookingPrice; return this; }

    public UserBookingSummaryDTO build() {
        return new UserBookingSummaryDTO(userId, name, totalBookings, completedBookings,
                cancelledBookings, totalSpent, averageBookingPrice);
    }
}
