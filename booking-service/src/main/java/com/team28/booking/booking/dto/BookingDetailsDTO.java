package com.team28.booking.booking.dto;

import com.team28.booking.booking.model.Booking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BookingDetailsDTO(
                Long bookingId,
                Long userId,
                Long providerId,
                Booking.Status status,
                BigDecimal totalPrice,
                Map<String, Object> metadata,
                List<ServiceDetailsDTO> services,
                Integer totalServices,
                Integer completedServices) {
}
