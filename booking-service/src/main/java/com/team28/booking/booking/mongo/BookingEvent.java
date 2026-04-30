package com.team28.booking.booking.mongo;

import com.team28.booking.booking.factory.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "booking_events")
public class BookingEvent implements MongoEvent {

    @Id
    private String id;

    @Indexed
    private Long bookingId;

    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public BookingEvent() {}

    public BookingEvent(String action, LocalDateTime timestamp, Map<String, Object> params) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = new HashMap<>(params);
        this.bookingId = params.get("bookingId") != null
                ? ((Number) params.get("bookingId")).longValue() : null;
    }

    @Override public String getId()                  { return id; }
    @Override public LocalDateTime getTimestamp()    { return timestamp; }
    @Override public String getAction()              { return action; }
    @Override public Map<String, Object> getDetails(){ return details; }

    public Long getBookingId() { return bookingId; }

    public void setId(String id)                            { this.id = id; }
    public void setBookingId(Long bookingId)                { this.bookingId = bookingId; }
    public void setAction(String action)                    { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp)       { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details)     { this.details = details; }
}
