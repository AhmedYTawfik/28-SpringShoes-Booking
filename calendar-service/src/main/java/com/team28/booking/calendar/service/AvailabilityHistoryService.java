package com.team28.booking.calendar.service;

import com.team28.booking.calendar.adapter.CassandraRowAdapter;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEvent;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEventRepository;
import com.team28.booking.calendar.dto.AvailabilitySnapshotDTO;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AvailabilityHistoryService {

    private final CalendarAvailabilityEventRepository eventRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final CassandraRowAdapter cassandraRowAdapter;

    public AvailabilityHistoryService(CalendarAvailabilityEventRepository eventRepository,
                                      TimeSlotRepository timeSlotRepository,
                                      CassandraRowAdapter cassandraRowAdapter) {
        this.eventRepository = eventRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.cassandraRowAdapter = cassandraRowAdapter;
    }

    /** S4-F12: provider availability history — 5 min TTL. */
    @Cacheable(cacheNames = "calendar-service::S4-F12",
               key = "T(java.util.Objects).hash(#providerId, #startTime, #endTime)")
    public List<AvailabilitySnapshotDTO> getAvailabilityHistory(Long providerId, Instant startTime, Instant endTime) {
        if (timeSlotRepository.countProviderById(providerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
        }

        List<CalendarAvailabilityEvent> rows = (startTime != null && endTime != null)
                ? eventRepository.findByProviderIdAndTimestampBetween(providerId, startTime, endTime)
                : eventRepository.findByProviderId(providerId);

        return rows.stream()
                .map(row -> cassandraRowAdapter.adapt(row, AvailabilitySnapshotDTO.class))
                .toList();
    }
}
