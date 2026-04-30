package com.team28.booking.user.mongo;

import com.team28.booking.user.factory.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {

    @Id
    private String id;

    @Indexed
    private Long userId;

    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public AuthEvent() {}

    public AuthEvent(String action, LocalDateTime timestamp, Map<String, Object> params) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = new HashMap<>(params);
        this.userId = params.get("userId") != null
                ? ((Number) params.get("userId")).longValue() : null;
    }

    @Override public String getId()                  { return id; }
    @Override public LocalDateTime getTimestamp()    { return timestamp; }
    @Override public String getAction()              { return action; }
    @Override public Map<String, Object> getDetails(){ return details; }

    public Long getUserId() { return userId; }

    public void setId(String id)                            { this.id = id; }
    public void setUserId(Long userId)                      { this.userId = userId; }
    public void setAction(String action)                    { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp)       { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details)     { this.details = details; }
}
