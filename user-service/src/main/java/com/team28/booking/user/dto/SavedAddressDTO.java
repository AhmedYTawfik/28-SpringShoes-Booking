package com.team28.booking.user.dto;

import java.util.Map;

public record SavedAddressDTO(
        String label,
        String address,
        Double lat,
        Double lng,
        Boolean isDefault,
        Map<String, Object> metadata
) {
}
