package com.team28.booking.booking.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingEventRepository extends MongoRepository<BookingEvent, String> {

    List<BookingEvent> findByBookingId(Long bookingId);

    List<BookingEvent> findByAction(String action);
}
