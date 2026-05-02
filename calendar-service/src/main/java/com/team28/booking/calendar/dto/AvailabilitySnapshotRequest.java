package com.team28.booking.calendar.dto;

import java.time.LocalDate;

/**
 * S4-F11: Request body for POST /api/calendar/{providerId}/availability-snapshot.
 */
public class AvailabilitySnapshotRequest {
    private LocalDate date;
    private String notes;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
