package com.team28.booking.user.factory;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        // Concrete case is added in the per-service DP-2-mongoevent branch (4C).
        throw new UnsupportedOperationException(
                "Concrete event class for " + type + " not yet registered in this service.");
    }
}
