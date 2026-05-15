package com.team28.booking.contracts.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record TimeSlotDTO(
        Long id,
        Long providerId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean available,
        Map<String, Object> metadata
) {
}
