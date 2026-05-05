package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("User")
public class UserNode {

    @Id
    private Long id;

    @Relationship(type = "BOOKED", direction = Relationship.Direction.OUTGOING)
    private List<BookedRelationship> bookings = new ArrayList<>();

    public UserNode() {}

    public UserNode(Long pgUserId) {
        this.id = pgUserId;
    }

    public Long getId()                             { return id; }
    public List<BookedRelationship> getBookings()   { return bookings; }

    public void setId(Long id)                                      { this.id = id; }
    public void setBookings(List<BookedRelationship> bookings)      { this.bookings = bookings; }
}
