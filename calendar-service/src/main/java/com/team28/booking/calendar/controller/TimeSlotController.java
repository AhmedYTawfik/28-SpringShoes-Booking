package com.team28.booking.calendar.controller;

import com.team28.booking.calendar.dto.AvailableProviderDTO;
import com.team28.booking.calendar.dto.IdleProviderDTO;
import com.team28.booking.calendar.dto.BatchTimeSlotRequest;
import com.team28.booking.calendar.dto.ProviderUtilizationDTO;
import com.team28.booking.calendar.model.TimeSlot;
import com.team28.booking.calendar.service.TimeSlotService;
import com.team28.booking.contracts.dto.TimeSlotDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timeslots")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeSlot create(@RequestBody TimeSlot timeSlot) {
        return timeSlotService.createTimeSlot(timeSlot);
    }

    @PostMapping("/provider/{providerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeSlot createForProvider(@PathVariable Long providerId, @RequestBody TimeSlot timeSlot) {
        return timeSlotService.createTimeSlotForProvider(providerId, timeSlot);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> batchCreate(@RequestBody BatchTimeSlotRequest request) {
        if (request.getProviderId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is missing");
        }
        int count = timeSlotService.batchCreateTimeSlots(request.getProviderId(), request.getTimeSlots());
        return Map.of("count", count);
    }

    @GetMapping
    public List<TimeSlot> getAll() {
        return timeSlotService.getAllTimeSlots();
    }

    @GetMapping("/available")
    public List<AvailableProviderDTO> getAvailableProviders(
            @RequestParam LocalDate date,
            @RequestParam(required = false) String specialty) {
        return timeSlotService.findAvailableProviders(date, specialty);
    }

    @GetMapping("/{id}")
    public TimeSlot getById(@PathVariable Long id) {
        return timeSlotService.getTimeSlotById(id);
    }

    @GetMapping("/provider/{providerId}/latest")
    public TimeSlot getLatestTimeSlot(@PathVariable Long providerId) {
        return timeSlotService.getLatestTimeSlot(providerId);
    }

    @GetMapping("/history")
    public List<TimeSlot> getHistory(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long providerId) {
        return timeSlotService.getHistory(startDate, endDate, providerId);
    }
      
    @GetMapping("/metadata/search")
    public List<TimeSlot> searchByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value) {
        return timeSlotService.searchByMetadata(key, operator, value);
    }

    @GetMapping("/idle")
    public List<IdleProviderDTO> getIdleProviders(
            @RequestParam int maxBookedSlots,
            @RequestParam int sinceDays) {
        return timeSlotService.findIdleProviders(maxBookedSlots, sinceDays);
    }
      
    @GetMapping("/provider/{providerId}/slot")
    public TimeSlotDTO getSlotForBooking(
            @PathVariable Long providerId,
            @RequestParam String date,
            @RequestParam String startTime) {
        return timeSlotService.getSlotForBooking(providerId, LocalDate.parse(date), LocalTime.parse(startTime));
    }

    @GetMapping("/provider/{providerId}/utilization")
    public ProviderUtilizationDTO getUtilization(
            @PathVariable Long providerId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return timeSlotService.getUtilization(providerId, startDate, endDate);
    }

    @PutMapping("/{id}")
    public TimeSlot update(@PathVariable Long id, @RequestBody TimeSlot timeSlot) {
        return timeSlotService.updateTimeSlot(id, timeSlot);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        timeSlotService.deleteTimeSlot(id);
    }

    @DeleteMapping("/purge")
    public Map<String, Integer> purge(@RequestParam int olderThanDays) {
        return timeSlotService.purgeOldSlots(olderThanDays);
    }
}
