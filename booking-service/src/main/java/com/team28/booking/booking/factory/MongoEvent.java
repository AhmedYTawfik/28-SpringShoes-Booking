package com.team28.booking.booking.factory;

import java.time.LocalDateTime;
import java.util.Map;

public interface MongoEvent {
    String getId();
    LocalDateTime getTimestamp();
    String getAction();
    Map<String, Object> getDetails();
}
