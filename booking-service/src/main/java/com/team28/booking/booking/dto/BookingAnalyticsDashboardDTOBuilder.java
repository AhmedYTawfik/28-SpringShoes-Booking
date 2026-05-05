package com.team28.booking.booking.dto;

import java.math.BigDecimal;
import java.util.Map;

public class BookingAnalyticsDashboardDTOBuilder {
    private long totalBookings;
    private BigDecimal totalRevenue;
    private BigDecimal averageBookingValue;
    private double completionRate;
    private Map<String, Long> bookingsByStatus;

    public BookingAnalyticsDashboardDTOBuilder totalBookings(long totalBookings) { this.totalBookings = totalBookings; return this; }
    public BookingAnalyticsDashboardDTOBuilder totalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public BookingAnalyticsDashboardDTOBuilder averageBookingValue(BigDecimal averageBookingValue) { this.averageBookingValue = averageBookingValue; return this; }
    public BookingAnalyticsDashboardDTOBuilder completionRate(double completionRate) { this.completionRate = completionRate; return this; }
    public BookingAnalyticsDashboardDTOBuilder bookingsByStatus(Map<String, Long> bookingsByStatus) { this.bookingsByStatus = bookingsByStatus; return this; }

    public BookingAnalyticsDashboardDTO build() {
        return new BookingAnalyticsDashboardDTO(totalBookings, totalRevenue, averageBookingValue,
                completionRate, bookingsByStatus);
    }
}