package com.team28.booking.calendar.factory;

import com.team28.booking.calendar.mongo.CalendarEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        String action = (String) params.get("action");
        LocalDateTime ts = LocalDateTime.now();
        if (type == EventType.CALENDAR) {
            return new CalendarEvent(action, ts, params);
        }
        throw new UnsupportedOperationException(
                "EventType " + type + " is not handled by calendar-service EventFactory.");
    }
}
