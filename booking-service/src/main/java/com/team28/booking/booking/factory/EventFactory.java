package com.team28.booking.booking.factory;

import com.team28.booking.booking.mongo.BookingEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        String action = (String) params.get("action");
        LocalDateTime ts = LocalDateTime.now();
        if (type == EventType.BOOKING) {
            return new BookingEvent(action, ts, params);
        }
        throw new UnsupportedOperationException(
                "EventType " + type + " is not handled by booking-service EventFactory.");
    }
}
