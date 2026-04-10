package com.team28.booking.calendar.dto;

public class AvailableProviderDTO {

    private Long providerId;
    private String providerName;
    private String specialty;
    private Double rating;
    private Long availableSlots;

    public AvailableProviderDTO(Long providerId, String providerName, String specialty,
                                Double rating, Long availableSlots) {
        this.providerId = providerId;
        this.providerName = providerName;
        this.specialty = specialty;
        this.rating = rating;
        this.availableSlots = availableSlots;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Long getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(Long availableSlots) {
        this.availableSlots = availableSlots;
    }
}
