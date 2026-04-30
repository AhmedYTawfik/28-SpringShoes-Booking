package com.team28.booking.calendar.mongo;

import com.team28.booking.calendar.factory.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "calendar_events")
public class CalendarEvent implements MongoEvent {

    @Id
    private String id;

    @Indexed
    private Long providerId;

    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public CalendarEvent() {}

    public CalendarEvent(String action, LocalDateTime timestamp, Map<String, Object> params) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = new HashMap<>(params);
        this.providerId = params.get("providerId") != null
                ? ((Number) params.get("providerId")).longValue() : null;
    }

    @Override public String getId()                  { return id; }
    @Override public LocalDateTime getTimestamp()    { return timestamp; }
    @Override public String getAction()              { return action; }
    @Override public Map<String, Object> getDetails(){ return details; }

    public Long getProviderId() { return providerId; }

    public void setId(String id)                            { this.id = id; }
    public void setProviderId(Long providerId)              { this.providerId = providerId; }
    public void setAction(String action)                    { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp)       { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details)     { this.details = details; }
}
