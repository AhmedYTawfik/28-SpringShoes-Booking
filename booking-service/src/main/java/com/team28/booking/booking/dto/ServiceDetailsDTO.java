package com.team28.booking.booking.dto;

import com.team28.booking.booking.model.BookingItem;

import java.math.BigDecimal;
import java.util.Map;

public record ServiceDetailsDTO(
        Long id,
        Integer serviceOrder,
        String serviceName,
        Integer duration,
        BigDecimal price,
        BookingItem.Status status,
        Map<String, Object> metadata
) {
}
