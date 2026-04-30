package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {

    Optional<UserNode> findByUserId(Long userId);
}
