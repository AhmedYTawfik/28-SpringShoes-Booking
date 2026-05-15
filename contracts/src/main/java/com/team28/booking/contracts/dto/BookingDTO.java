package com.team28.booking.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

public record BookingDTO(
        Long id,
        Long userId,
        Long providerId,
        String status,
        BigDecimal totalPrice,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime completedAt,
        Map<String, Object> metadata
) {
}
