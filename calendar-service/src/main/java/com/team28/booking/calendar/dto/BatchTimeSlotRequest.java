package com.team28.booking.calendar.dto;

import com.team28.booking.calendar.model.TimeSlot;

import java.util.List;

public class BatchTimeSlotRequest {
    private Long providerId;
    private List<TimeSlot> timeSlots;

    public BatchTimeSlotRequest() {}

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public List<TimeSlot> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<TimeSlot> timeSlots) {
        this.timeSlots = timeSlots;
    }

    public List<TimeSlot> getSlots() {
        return timeSlots;
    }

    public void setSlots(List<TimeSlot> slots) {
        this.timeSlots = slots;
    }
}
