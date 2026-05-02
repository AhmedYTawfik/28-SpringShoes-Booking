package com.team28.booking.calendar.controller;

import com.team28.booking.calendar.dto.AvailabilitySnapshotDTO;
import com.team28.booking.calendar.service.AvailabilityHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
public class AvailabilityHistoryController {

    private final AvailabilityHistoryService availabilityHistoryService;

    public AvailabilityHistoryController(AvailabilityHistoryService availabilityHistoryService) {
        this.availabilityHistoryService = availabilityHistoryService;
    }

    @GetMapping("/{providerId}/availability-history")
    public List<AvailabilitySnapshotDTO> getAvailabilityHistory(
            @PathVariable Long providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        if ((startTime == null) ^ (endTime == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startTime and endTime must be provided together");
        }
        if (startTime != null && startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startTime must be before or equal to endTime");
        }
        return availabilityHistoryService.getAvailabilityHistory(providerId, startTime, endTime);
    }
}
