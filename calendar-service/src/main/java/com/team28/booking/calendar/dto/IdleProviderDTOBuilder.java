package com.team28.booking.calendar.dto;

public class IdleProviderDTOBuilder {
    private Long providerId;
    private String providerName;
    private String specialty;
    private Double rating;
    private Long bookedSlotsCount;
    private Long totalSlotsCount;

    public IdleProviderDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public IdleProviderDTOBuilder providerName(String providerName) { this.providerName = providerName; return this; }
    public IdleProviderDTOBuilder specialty(String specialty) { this.specialty = specialty; return this; }
    public IdleProviderDTOBuilder rating(Double rating) { this.rating = rating; return this; }
    public IdleProviderDTOBuilder bookedSlotsCount(Long bookedSlotsCount) { this.bookedSlotsCount = bookedSlotsCount; return this; }
    public IdleProviderDTOBuilder totalSlotsCount(Long totalSlotsCount) { this.totalSlotsCount = totalSlotsCount; return this; }

    public IdleProviderDTO build() {
        return new IdleProviderDTO(providerId, providerName, specialty, rating, bookedSlotsCount, totalSlotsCount);
    }
}
