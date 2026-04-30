package com.team28.booking.invoice.factory;

import com.team28.booking.invoice.mongo.PaymentAuditEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        String action = (String) params.get("action");
        LocalDateTime ts = LocalDateTime.now();
        if (type == EventType.PAYMENT_AUDIT) {
            return new PaymentAuditEvent(action, ts, params);
        }
        throw new UnsupportedOperationException(
                "EventType " + type + " is not handled by invoice-service EventFactory.");
    }
}
