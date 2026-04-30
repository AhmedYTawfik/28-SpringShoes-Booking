package com.team28.booking.calendar.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * All queries include provider_id (partition key) per §7.4 Cassandra requirement.
 */
@Repository
public interface CalendarAvailabilityEventRepository
        extends CassandraRepository<CalendarAvailabilityEvent, Long> {

    List<CalendarAvailabilityEvent> findByProviderId(Long providerId);

    List<CalendarAvailabilityEvent> findByProviderIdAndTimestampBetween(
            Long providerId, Instant from, Instant to);
}
