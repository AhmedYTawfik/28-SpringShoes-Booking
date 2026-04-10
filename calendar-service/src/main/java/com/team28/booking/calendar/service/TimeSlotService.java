package com.team28.booking.calendar.service;

import com.team28.booking.calendar.model.TimeSlot;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    public TimeSlotService(TimeSlotRepository timeSlotRepository) {
        this.timeSlotRepository = timeSlotRepository;
    }

    public TimeSlot createTimeSlot(TimeSlot timeSlot) {
        timeSlot.setCreatedAt(LocalDateTime.now());
        timeSlot.setAvailable(true);
        return timeSlotRepository.save(timeSlot);
    }

    public List<TimeSlot> getAllTimeSlots() {
        return timeSlotRepository.findAll();
    }

    public TimeSlot getTimeSlotById(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "TimeSlot not found with id: " + id));
    }

    public TimeSlot updateTimeSlot(Long id, TimeSlot updated) {
        TimeSlot existing = getTimeSlotById(id);
        if (updated.getId() != null && !id.equals(updated.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "TimeSlot ID in request body must match path ID");
        }
        existing.setDate(updated.getDate());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setAvailable(updated.getAvailable());
        existing.setMetadata(updated.getMetadata());
        return timeSlotRepository.save(existing);
    }

    public void deleteTimeSlot(Long id) {
        TimeSlot existing = getTimeSlotById(id);
        timeSlotRepository.delete(existing);
    }
}
