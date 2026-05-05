package com.team28.booking.calendar.dto;

import java.time.Instant;
import java.util.Objects;

public class AvailabilitySnapshotDTO {

    private Long providerId;
    private Instant timestamp;
    private String date;
    private Integer totalSlots;
    private Integer availableSlots;
    private Integer bookedSlots;
    private Double utilizationRate;
    private String notes;

    public AvailabilitySnapshotDTO() {}

    public AvailabilitySnapshotDTO(Long providerId, Instant timestamp, String date,
                                   Integer totalSlots, Integer availableSlots, Integer bookedSlots,
                                   Double utilizationRate, String notes) {
        this.providerId = providerId;
        this.timestamp = timestamp;
        this.date = date;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.bookedSlots = bookedSlots;
        this.utilizationRate = utilizationRate;
        this.notes = notes;
    }

    public static AvailabilitySnapshotDTOBuilder builder() { return new AvailabilitySnapshotDTOBuilder(); }

    public Long getProviderId()         { return providerId; }
    public Instant getTimestamp()       { return timestamp; }
    public String getDate()             { return date; }
    public Integer getTotalSlots()      { return totalSlots; }
    public Integer getAvailableSlots()  { return availableSlots; }
    public Integer getBookedSlots()     { return bookedSlots; }
    public Double getUtilizationRate()  { return utilizationRate; }
    public String getNotes()            { return notes; }

    public void setProviderId(Long providerId)              { this.providerId = providerId; }
    public void setTimestamp(Instant timestamp)             { this.timestamp = timestamp; }
    public void setDate(String date)                        { this.date = date; }
    public void setTotalSlots(Integer totalSlots)           { this.totalSlots = totalSlots; }
    public void setAvailableSlots(Integer availableSlots)   { this.availableSlots = availableSlots; }
    public void setBookedSlots(Integer bookedSlots)         { this.bookedSlots = bookedSlots; }
    public void setUtilizationRate(Double utilizationRate)  { this.utilizationRate = utilizationRate; }
    public void setNotes(String notes)                      { this.notes = notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AvailabilitySnapshotDTO that)) return false;
        return Objects.equals(providerId, that.providerId)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(date, that.date)
                && Objects.equals(totalSlots, that.totalSlots)
                && Objects.equals(availableSlots, that.availableSlots)
                && Objects.equals(bookedSlots, that.bookedSlots)
                && Objects.equals(utilizationRate, that.utilizationRate)
                && Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, timestamp, date, totalSlots, availableSlots, bookedSlots, utilizationRate, notes);
    }
}
