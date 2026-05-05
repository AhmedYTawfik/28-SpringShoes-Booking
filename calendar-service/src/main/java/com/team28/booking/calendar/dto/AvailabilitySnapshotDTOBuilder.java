package com.team28.booking.calendar.dto;

import java.time.Instant;

public class AvailabilitySnapshotDTOBuilder {
    private Long providerId;
    private Instant timestamp;
    private String date;
    private Integer totalSlots;
    private Integer availableSlots;
    private Integer bookedSlots;
    private Double utilizationRate;
    private String notes;

    public AvailabilitySnapshotDTOBuilder providerId(Long providerId) { this.providerId = providerId; return this; }
    public AvailabilitySnapshotDTOBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
    public AvailabilitySnapshotDTOBuilder date(String date) { this.date = date; return this; }
    public AvailabilitySnapshotDTOBuilder totalSlots(Integer totalSlots) { this.totalSlots = totalSlots; return this; }
    public AvailabilitySnapshotDTOBuilder availableSlots(Integer availableSlots) { this.availableSlots = availableSlots; return this; }
    public AvailabilitySnapshotDTOBuilder bookedSlots(Integer bookedSlots) { this.bookedSlots = bookedSlots; return this; }
    public AvailabilitySnapshotDTOBuilder utilizationRate(Double utilizationRate) { this.utilizationRate = utilizationRate; return this; }
    public AvailabilitySnapshotDTOBuilder notes(String notes) { this.notes = notes; return this; }

    public AvailabilitySnapshotDTO build() {
        return new AvailabilitySnapshotDTO(providerId, timestamp, date, totalSlots, availableSlots, bookedSlots, utilizationRate, notes);
    }
}
