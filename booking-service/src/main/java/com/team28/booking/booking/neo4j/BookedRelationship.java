package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;

/**
 * (User)-[:BOOKED]->(Provider) relationship per §7.3.
 */
@RelationshipProperties
public class BookedRelationship {

    @Id
    @GeneratedValue
    private Long id;

    private Integer bookingCount;

    private LocalDateTime lastBookingDate;

    @TargetNode
    private ProviderNode provider;

    public BookedRelationship() {}

    public BookedRelationship(ProviderNode provider, Integer bookingCount, LocalDateTime lastBookingDate) {
        this.provider = provider;
        this.bookingCount = bookingCount;
        this.lastBookingDate = lastBookingDate;
    }

    public Long getId()                         { return id; }
    public Integer getBookingCount()            { return bookingCount; }
    public LocalDateTime getLastBookingDate()   { return lastBookingDate; }
    public ProviderNode getProvider()           { return provider; }

    public void setId(Long id)                                  { this.id = id; }
    public void setBookingCount(Integer bookingCount)           { this.bookingCount = bookingCount; }
    public void setLastBookingDate(LocalDateTime lastBookingDate){ this.lastBookingDate = lastBookingDate; }
    public void setProvider(ProviderNode provider)              { this.provider = provider; }
}
