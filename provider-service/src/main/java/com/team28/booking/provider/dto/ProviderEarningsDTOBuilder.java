package com.team28.booking.provider.dto;

public class ProviderEarningsDTOBuilder {
    private Long providerId;
    private String name;
    private Long totalBookings;
    private Double totalEarnings;
    private Double averageBookingPrice;

    public ProviderEarningsDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public ProviderEarningsDTOBuilder name(String name) { this.name = name; return this; }
    public ProviderEarningsDTOBuilder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
    public ProviderEarningsDTOBuilder totalEarnings(Double totalEarnings) { this.totalEarnings = totalEarnings; return this; }
    public ProviderEarningsDTOBuilder averageBookingPrice(Double averageBookingPrice) { this.averageBookingPrice = averageBookingPrice; return this; }

    public ProviderEarningsDTO build() {
        return new ProviderEarningsDTO(providerId, name, totalBookings, totalEarnings, averageBookingPrice);
    }
}
