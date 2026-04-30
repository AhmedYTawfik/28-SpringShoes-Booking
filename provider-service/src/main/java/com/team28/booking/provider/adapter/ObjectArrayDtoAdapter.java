package com.team28.booking.provider.adapter;

import com.team28.booking.provider.dto.ProviderEarningsDTO;
import org.springframework.stereotype.Component;

@Component
public class ObjectArrayDtoAdapter {

    public ProviderEarningsDTO toProviderEarningsDTO(Long providerId, String providerName, Object[] row) {
        Long totalBookings = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        Double totalEarnings = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
        Double averageBookingPrice = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
        return new ProviderEarningsDTO(providerId, providerName, totalBookings, totalEarnings, averageBookingPrice);
    }
}
