package com.team28.booking.provider.dto;

public class TopProviderDTOBuilder {
    private Long providerId;
    private String name;
    private Double rating;
    private Integer totalBookings;

    public TopProviderDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public TopProviderDTOBuilder name(String name) { this.name = name; return this; }
    public TopProviderDTOBuilder rating(Double rating) { this.rating = rating; return this; }
    public TopProviderDTOBuilder totalBookings(Integer totalBookings) { this.totalBookings = totalBookings; return this; }

    public TopProviderDTO build() {
        return new TopProviderDTO(providerId, name, rating, totalBookings);
    }
}
