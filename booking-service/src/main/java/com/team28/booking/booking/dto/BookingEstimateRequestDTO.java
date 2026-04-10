package com.team28.booking.booking.dto;

import java.time.LocalDate;
import java.util.List;

public record BookingEstimateRequestDTO(
        Long providerId,
        LocalDate appointmentDate,
        List<EstimateServiceItemDTO> services
) {
}
