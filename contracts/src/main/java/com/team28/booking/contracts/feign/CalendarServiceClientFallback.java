package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.ProviderUtilizationDTO;
import com.team28.booking.contracts.dto.TimeSlotDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Component
public class CalendarServiceClientFallback implements CalendarServiceClient {
    @Override
    public TimeSlotDTO getSlotForBooking(Long providerId, String date, String startTime) {
        LocalDate parsedDate = date != null ? LocalDate.parse(date) : null;
        LocalTime parsedStart = startTime != null ? LocalTime.parse(startTime) : null;
        return new TimeSlotDTO(null, providerId, parsedDate, parsedStart, null, false, Map.of("fallback", true));
    }

    @Override
    public ProviderUtilizationDTO getProviderUtilization(Long providerId, String startDate, String endDate) {
        return new ProviderUtilizationDTO(providerId, 0, 0, 0, 0.0);
    }
}
