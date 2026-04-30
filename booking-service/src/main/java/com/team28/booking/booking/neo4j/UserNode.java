package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("User")
public class UserNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("userId")
    private Long userId;

    @Relationship(type = "BOOKED", direction = Relationship.Direction.OUTGOING)
    private List<BookedRelationship> bookings = new ArrayList<>();

    public UserNode() {}

    public UserNode(Long userId) {
        this.userId = userId;
    }

    public Long getId()                             { return id; }
    public Long getUserId()                         { return userId; }
    public List<BookedRelationship> getBookings()   { return bookings; }

    public void setId(Long id)                                      { this.id = id; }
    public void setUserId(Long userId)                              { this.userId = userId; }
    public void setBookings(List<BookedRelationship> bookings)      { this.bookings = bookings; }
}
