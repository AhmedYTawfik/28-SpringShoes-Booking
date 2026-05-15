package com.team28.booking.contracts.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record UserDTO(
        Long id,
        String name,
        String email,
        String role,
        String status,
        String phone,
        Map<String, Object> preferences,
        LocalDateTime createdAt
) {
}
