package com.team28.booking.calendar.service;

import com.team28.booking.calendar.model.TimeSlot;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public TimeSlot createTimeSlotForProvider(Long providerId, TimeSlot timeSlot) {
        Long providerCount = timeSlotRepository.countProviderById(providerId);
        if (providerCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
        }

        validateTimeRange(timeSlot);
        timeSlot.setProviderId(providerId);
        timeSlot.setAvailable(true);
        timeSlot.setCreatedAt(LocalDateTime.now());
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

    public TimeSlot getLatestTimeSlot(Long providerId) {
        Long providerCount = timeSlotRepository.countProviderById(providerId);
        if (providerCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
        }

        return timeSlotRepository.findTopByProviderIdOrderByDateDescStartTimeDesc(providerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No time slots found for provider"));
    }

    public List<TimeSlot> getHistory(LocalDate startDate, LocalDate endDate, Long providerId) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }

        return timeSlotRepository.findByDateRangeAndProvider(startDate, endDate, providerId);
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

    public List<TimeSlot> searchByMetadata(String key, String operator, String value) {
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "eq" -> timeSlotRepository.findByMetadataEquals(key, value);
            case "gt" -> timeSlotRepository.findByMetadataGreaterThan(key, value);
            case "lt" -> timeSlotRepository.findByMetadataLessThan(key, value);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid operator: " + operator + ". Must be eq, gt, or lt");
        };
    }

    @Transactional
    public Map<String, Integer> purgeOldSlots(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "olderThanDays must be greater than or equal to 0");
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(olderThanDays);
        int deletedCount = timeSlotRepository.countByDateBefore(cutoffDate);
        timeSlotRepository.deleteByDateBefore(cutoffDate);
        return Map.of("deletedCount", deletedCount);
    }

    private void validateTimeRange(TimeSlot timeSlot) {
        if (timeSlot.getStartTime() == null
                || timeSlot.getEndTime() == null
                || !timeSlot.getStartTime().isBefore(timeSlot.getEndTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }
    }
}
