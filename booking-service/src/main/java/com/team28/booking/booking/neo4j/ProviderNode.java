package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node("Provider")
public class ProviderNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("providerId")
    private Long providerId;

    public ProviderNode() {}

    public ProviderNode(Long providerId) {
        this.providerId = providerId;
    }

    public Long getId()         { return id; }
    public Long getProviderId() { return providerId; }

    public void setId(Long id)                  { this.id = id; }
    public void setProviderId(Long providerId)  { this.providerId = providerId; }
}
