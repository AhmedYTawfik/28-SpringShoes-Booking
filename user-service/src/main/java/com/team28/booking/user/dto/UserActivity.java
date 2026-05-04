package com.team28.booking.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

record UserActivity(
        String action,
        LocalDateTime timestamp,
        Map<String, Object> details) {
}
