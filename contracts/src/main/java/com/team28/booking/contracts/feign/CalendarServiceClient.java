package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.ProviderUtilizationDTO;
import com.team28.booking.contracts.dto.TimeSlotDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@FeignClient(name = "calendar-service", url = "${feign.calendar-service.url}")
public interface CalendarServiceClient {
    @GetMapping("/api/timeslots/provider/{providerId}/slot")
    TimeSlotDTO getSlotForBooking(
            @PathVariable("providerId") Long providerId,
            @RequestParam("date") String date,
            @RequestParam("startTime") String startTime
    );

    @GetMapping("/api/timeslots/provider/{providerId}/utilization")
    ProviderUtilizationDTO getProviderUtilization(
            @PathVariable("providerId") Long providerId,
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate
    );
}
