package com.team28.booking.calendar.service;

import com.team28.booking.calendar.adapter.CassandraRowAdapter;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEvent;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEventRepository;
import com.team28.booking.calendar.dto.AvailabilitySnapshotDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AvailabilityHistoryService {

    private final CalendarAvailabilityEventRepository eventRepository;
    private final CassandraRowAdapter cassandraRowAdapter;

    public AvailabilityHistoryService(CalendarAvailabilityEventRepository eventRepository,
                                      CassandraRowAdapter cassandraRowAdapter) {
        this.eventRepository = eventRepository;
        this.cassandraRowAdapter = cassandraRowAdapter;
    }

    /** S4-F12: provider availability history — 5 min TTL. */
    @Cacheable(cacheNames = "calendar-service::S4-F12",
               key = "#providerId + '::' + T(java.util.Objects).hash(#startTime, #endTime)")
    public List<AvailabilitySnapshotDTO> getAvailabilityHistory(Long providerId, Instant startTime, Instant endTime) {
        if (startTime == null && endTime != null) {
            startTime = Instant.EPOCH;
        } else if (startTime != null && endTime == null) {
            endTime = Instant.parse("9999-12-31T23:59:59Z");
        }

        List<CalendarAvailabilityEvent> rows = (startTime != null && endTime != null)
                ? eventRepository.findByProviderIdAndTimestampBetween(providerId, startTime, endTime)
                : eventRepository.findByProviderId(providerId);

        return rows.stream()
                .map(row -> cassandraRowAdapter.adapt(row, AvailabilitySnapshotDTO.class))
                .toList();
    }
}
