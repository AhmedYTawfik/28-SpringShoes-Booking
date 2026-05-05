package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Provider")
public class ProviderNode {

    @Id
    private Long id;

    public ProviderNode() {}

    public ProviderNode(Long pgProviderId) {
        this.id = pgProviderId;
    }

    public Long getId()         { return id; }

    public void setId(Long id)  { this.id = id; }
}
