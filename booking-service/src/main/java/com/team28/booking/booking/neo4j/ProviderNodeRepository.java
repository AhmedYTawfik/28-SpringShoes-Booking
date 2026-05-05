package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderNodeRepository extends Neo4jRepository<ProviderNode, Long> {

    // findById(Long) inherited from Neo4jRepository — use that instead
}
