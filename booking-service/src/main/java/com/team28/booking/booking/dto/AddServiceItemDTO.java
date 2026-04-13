package com.team28.booking.booking.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AddServiceItemDTO(
        String serviceName,
        Integer duration,
        BigDecimal price,
        Map<String, Object> metadata
) {
}
