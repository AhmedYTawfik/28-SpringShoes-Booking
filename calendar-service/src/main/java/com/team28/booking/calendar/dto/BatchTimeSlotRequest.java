package com.team28.booking.calendar.dto;

import com.team28.booking.calendar.model.TimeSlot;

import java.util.List;

public record BatchTimeSlotRequest(Long providerId, List<TimeSlot> timeSlots) {
}
