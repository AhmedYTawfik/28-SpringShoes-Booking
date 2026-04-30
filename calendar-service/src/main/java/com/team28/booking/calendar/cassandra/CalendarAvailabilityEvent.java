package com.team28.booking.calendar.cassandra;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Cassandra entity per §7.4.1.
 * Partition key: provider_id (all queries MUST include provider_id in WHERE).
 * Clustering: timestamp DESC for newest-first reads.
 */
@Table("calendar_availability_events")
public class CalendarAvailabilityEvent {

    @PrimaryKeyColumn(name = "provider_id", type = PrimaryKeyType.PARTITIONED)
    private Long providerId;

    @PrimaryKeyColumn(name = "timestamp", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant timestamp;

    @Column("date")
    @CassandraType(type = CassandraType.Name.TEXT)
    private String date;

    @Column("total_slots")
    private Integer totalSlots;

    @Column("available_slots")
    private Integer availableSlots;

    @Column("booked_slots")
    private Integer bookedSlots;

    @Column("utilization_rate")
    private Double utilizationRate;

    @Column("notes")
    @CassandraType(type = CassandraType.Name.TEXT)
    private String notes;

    public CalendarAvailabilityEvent() {}

    public CalendarAvailabilityEvent(Long providerId, Instant timestamp, String date,
                                     Integer totalSlots, Integer availableSlots, Integer bookedSlots,
                                     Double utilizationRate, String notes) {
        this.providerId = providerId;
        this.timestamp = timestamp;
        this.date = date;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.bookedSlots = bookedSlots;
        this.utilizationRate = utilizationRate;
        this.notes = notes;
    }

    public Long getProviderId()         { return providerId; }
    public Instant getTimestamp()       { return timestamp; }
    public String getDate()             { return date; }
    public Integer getTotalSlots()      { return totalSlots; }
    public Integer getAvailableSlots()  { return availableSlots; }
    public Integer getBookedSlots()     { return bookedSlots; }
    public Double getUtilizationRate()  { return utilizationRate; }
    public String getNotes()            { return notes; }

    public void setProviderId(Long providerId)              { this.providerId = providerId; }
    public void setTimestamp(Instant timestamp)             { this.timestamp = timestamp; }
    public void setDate(String date)                        { this.date = date; }
    public void setTotalSlots(Integer totalSlots)           { this.totalSlots = totalSlots; }
    public void setAvailableSlots(Integer availableSlots)   { this.availableSlots = availableSlots; }
    public void setBookedSlots(Integer bookedSlots)         { this.bookedSlots = bookedSlots; }
    public void setUtilizationRate(Double utilizationRate)  { this.utilizationRate = utilizationRate; }
    public void setNotes(String notes)                      { this.notes = notes; }
}
