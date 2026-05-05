package com.team28.booking.provider.dto;

public class ProviderDashboardDTOBuilder {
    private Long providerId;
    private String name;
    private Long totalBookings;
    private Double totalRevenue;
    private Double averageBookingValue;
    private Double averageRating;
    private Double utilizationRate;

    public ProviderDashboardDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public ProviderDashboardDTOBuilder name(String name) { this.name = name; return this; }
    public ProviderDashboardDTOBuilder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
    public ProviderDashboardDTOBuilder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
    public ProviderDashboardDTOBuilder averageBookingValue(Double averageBookingValue) { this.averageBookingValue = averageBookingValue; return this; }
    public ProviderDashboardDTOBuilder averageRating(Double averageRating) { this.averageRating = averageRating; return this; }
    public ProviderDashboardDTOBuilder utilizationRate(Double utilizationRate) { this.utilizationRate = utilizationRate; return this; }

    public ProviderDashboardDTO build() {
        return new ProviderDashboardDTO(providerId, name, totalBookings, totalRevenue, averageBookingValue, averageRating, utilizationRate);
    }
}