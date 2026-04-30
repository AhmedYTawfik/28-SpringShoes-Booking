package com.team28.booking.calendar.dto;

public class ProviderUtilizationDTOBuilder {
    private Long providerId;
    private Long totalSlots;
    private Long bookedSlots;
    private Long availableSlots;
    private Double utilizationRate;
    private String peakDay;

    public ProviderUtilizationDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public ProviderUtilizationDTOBuilder totalSlots(Long totalSlots) { this.totalSlots = totalSlots; return this; }
    public ProviderUtilizationDTOBuilder bookedSlots(Long bookedSlots) { this.bookedSlots = bookedSlots; return this; }
    public ProviderUtilizationDTOBuilder availableSlots(Long availableSlots) { this.availableSlots = availableSlots; return this; }
    public ProviderUtilizationDTOBuilder utilizationRate(Double utilizationRate) { this.utilizationRate = utilizationRate; return this; }
    public ProviderUtilizationDTOBuilder peakDay(String peakDay) { this.peakDay = peakDay; return this; }

    public ProviderUtilizationDTO build() {
        return new ProviderUtilizationDTO(providerId, totalSlots, bookedSlots, availableSlots, utilizationRate, peakDay);
    }
}
