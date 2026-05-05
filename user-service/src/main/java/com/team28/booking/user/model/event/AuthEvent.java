package com.team28.booking.user.model.event;

import jakarta.persistence.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {
    @Id
    private String id;
    private Long userId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    @Override
    public String getId() {
        return "";
    }

    @Override
    public LocalDateTime getTimestamp() {
        return null;
    }

    @Override
    public String getAction() {
        return "";
    }

    @Override
    public Map<String, Object> getDetails() {
        return Map.of();
    }

    // constructors, getters, setters
}