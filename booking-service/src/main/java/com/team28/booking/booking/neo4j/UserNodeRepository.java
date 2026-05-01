package com.team28.booking.booking.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {

    Optional<UserNode> findByUserId(Long userId);

    /**
     * S3-F12 — Collaborative-filtering recommendation graph traversal.
     *
     * Algorithm (spec §10.3.3 step d):
     *  1. Start from the target user (userId = :userId).
     *  2. Find all providers that user booked (via BOOKED relationships).
     *  3. Find other users who share at least one of those providers ("similar users").
     *  4. Find providers those similar users booked.
     *  5. Exclude providers already booked by the target user.
     *  6. Count how many similar users booked each candidate provider (the "score").
     *  7. Return providerId + score ordered by score DESC, limited to :limit rows.
     *
     * Returns a list of maps with keys: providerId (Long), score (Long).
     */
    @Query("""
        MATCH (u:User {userId: $userId})-[:BOOKED]->(shared:Provider)<-[:BOOKED]-(similar:User)
        WHERE similar.userId <> $userId
        MATCH (similar)-[:BOOKED]->(candidate:Provider)
        WHERE NOT (u)-[:BOOKED]->(candidate)
        RETURN candidate.providerId AS providerId, count(distinct similar) AS score
        ORDER BY score DESC
        LIMIT $limit
        """)
    List<Map<String, Object>> findRecommendations(
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
