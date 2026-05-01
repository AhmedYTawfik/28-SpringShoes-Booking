package com.team28.booking.booking.dto;

import java.math.BigDecimal;

public class BookingAnalyticsDTOBuilder {
    private long totalBookings;
    private long completedBookings;
    private long cancelledBookings;
    private BigDecimal totalRevenue;
    private BigDecimal averageBookingPrice;
    private double completionRate;

    public BookingAnalyticsDTOBuilder totalBookings(long totalBookings) { this.totalBookings = totalBookings; return this; }
    public BookingAnalyticsDTOBuilder completedBookings(long completedBookings) { this.completedBookings = completedBookings; return this; }
    public BookingAnalyticsDTOBuilder cancelledBookings(long cancelledBookings) { this.cancelledBookings = cancelledBookings; return this; }
    public BookingAnalyticsDTOBuilder totalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public BookingAnalyticsDTOBuilder averageBookingPrice(BigDecimal averageBookingPrice) { this.averageBookingPrice = averageBookingPrice; return this; }
    public BookingAnalyticsDTOBuilder completionRate(double completionRate) { this.completionRate = completionRate; return this; }

    public BookingAnalyticsDTO build() {
        return new BookingAnalyticsDTO(totalBookings, completedBookings, cancelledBookings,
                totalRevenue, averageBookingPrice, completionRate);
    }
}
