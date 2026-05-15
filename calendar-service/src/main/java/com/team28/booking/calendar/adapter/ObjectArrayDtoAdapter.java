package com.team28.booking.calendar.adapter;

import com.team28.booking.calendar.dto.ProviderUtilizationDTO;
import org.springframework.stereotype.Component;

@Component
public class ObjectArrayDtoAdapter {

    public ProviderUtilizationDTO toProviderUtilizationDTO(Long providerId, Object[] row, Double utilizationRate, String peakDay) {
        Long totalSlots = ((Number) row[0]).longValue();
        Long bookedSlots = ((Number) row[1]).longValue();
        Long availableSlots = ((Number) row[2]).longValue();
        return new ProviderUtilizationDTO(providerId, totalSlots, bookedSlots, availableSlots, utilizationRate, peakDay);
    }
}
