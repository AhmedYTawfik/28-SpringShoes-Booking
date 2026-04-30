package com.team28.booking.provider.factory;

import com.team28.booking.provider.mongo.ProviderEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        String action = (String) params.get("action");
        LocalDateTime ts = LocalDateTime.now();
        if (type == EventType.PROVIDER) {
            return new ProviderEvent(action, ts, params);
        }
        throw new UnsupportedOperationException(
                "EventType " + type + " is not handled by provider-service EventFactory.");
    }
}
