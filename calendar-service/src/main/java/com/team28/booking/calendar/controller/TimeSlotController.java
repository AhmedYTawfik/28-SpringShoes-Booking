package com.team28.booking.calendar.controller;

import com.team28.booking.calendar.model.TimeSlot;
import com.team28.booking.calendar.service.TimeSlotService;
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

import java.util.List;

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

    @GetMapping
    public List<TimeSlot> getAll() {
        return timeSlotService.getAllTimeSlots();
    }

    @GetMapping("/{id}")
    public TimeSlot getById(@PathVariable Long id) {
        return timeSlotService.getTimeSlotById(id);
    }

    @GetMapping("/provider/{providerId}/latest")
    public TimeSlot getLatestTimeSlot(@PathVariable Long providerId) {
        return timeSlotService.getLatestTimeSlot(providerId);
    }

    @GetMapping("/metadata/search")
    public List<TimeSlot> searchByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value) {
        return timeSlotService.searchByMetadata(key, operator, value);
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
}
