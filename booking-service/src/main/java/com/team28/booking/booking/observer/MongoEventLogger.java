package com.team28.booking.booking.observer;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.factory.EventFactory;
import com.team28.booking.booking.factory.EventType;
import com.team28.booking.booking.factory.MongoEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    // Actions that mutate booking state and should trigger cache invalidation (§4.4.4)
    private static final Set<String> INVALIDATING_ACTIONS = Set.of(
            "BOOKING_CREATED", "BOOKING_DELETED", "PROVIDER_ASSIGNED",
            "BOOKING_COMPLETED", "BOOKING_CANCELLED", "SERVICES_ADDED"
    );

    private final EventFactory factory;
    private final MongoTemplate mongoTemplate;
    private final CacheInvalidator cacheInvalidator;

    @Value("${spring.application.name}")
    private String appName;

    private EventType boundType;

    public MongoEventLogger(EventFactory factory, MongoTemplate mongoTemplate, CacheInvalidator cacheInvalidator) {
        this.factory = factory;
        this.mongoTemplate = mongoTemplate;
        this.cacheInvalidator = cacheInvalidator;
    }

    @PostConstruct
    private void init() {
        this.boundType = switch (appName) {
            case "user-service"     -> EventType.AUTH;
            case "provider-service" -> EventType.PROVIDER;
            case "booking-service"  -> EventType.BOOKING;
            case "calendar-service" -> EventType.CALENDAR;
            case "invoice-service"  -> EventType.PAYMENT_AUDIT;
            default -> throw new IllegalStateException("Unknown service name for MongoEventLogger: " + appName);
        };
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("action", eventType);
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) m;
                params.putAll(typedMap);
            } else if (payload != null) {
                params.put("payload", payload);
            }
            MongoEvent event = factory.createEvent(boundType, params);
            mongoTemplate.save(event);
            // Observer-driven cache invalidation after successful Mongo write (§4.4.4)
            // Exclude ANALYTICS_VIEWED / DASHBOARD_VIEWED — they must not self-defeat the cache
            if (INVALIDATING_ACTIONS.contains(eventType)) {
                cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
                cacheInvalidator.wildcardDelete("booking-service::S3-F12::*");
            }
        } catch (Exception ex) {
            log.warn("MongoEventLogger failed for {} action={}: {}", boundType, eventType, ex.getMessage());
            // intentional: do NOT rethrow — §3.3 failure policy
        }
    }
}
