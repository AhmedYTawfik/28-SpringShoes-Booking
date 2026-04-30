package com.team28.booking.calendar.dto;

public class AvailableProviderDTOBuilder {
    private Long providerId;
    private String providerName;
    private String specialty;
    private Double rating;
    private Long availableSlots;

    public AvailableProviderDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public AvailableProviderDTOBuilder providerName(String providerName) { this.providerName = providerName; return this; }
    public AvailableProviderDTOBuilder specialty(String specialty) { this.specialty = specialty; return this; }
    public AvailableProviderDTOBuilder rating(Double rating) { this.rating = rating; return this; }
    public AvailableProviderDTOBuilder availableSlots(Long availableSlots) { this.availableSlots = availableSlots; return this; }

    public AvailableProviderDTO build() {
        return new AvailableProviderDTO(providerId, providerName, specialty, rating, availableSlots);
    }
}
