package com.team28.booking.calendar.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {

    List<CalendarEvent> findByProviderId(Long providerId);

    List<CalendarEvent> findByAction(String action);
}
