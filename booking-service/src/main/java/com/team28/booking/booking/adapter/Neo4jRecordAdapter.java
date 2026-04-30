package com.team28.booking.booking.adapter;

import com.team28.booking.booking.neo4j.UserNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DP-7 Adapter: converts a Neo4j result record (as Map) to a target DTO type.
 * Used by S3-F12 recommendation reads to decouple graph rows from DTOs.
 */
@Component
public class Neo4jRecordAdapter {

    public <T> T adapt(Map<String, Object> record, Class<T> targetType) {
        if (record == null) return null;
        try {
            T instance = targetType.getDeclaredConstructor().newInstance();
            record.forEach((key, value) -> {
                try {
                    var field = targetType.getDeclaredField(key);
                    field.setAccessible(true);
                    field.set(instance, value);
                } catch (NoSuchFieldException ignored) {
                } catch (Exception ex) {
                    throw new RuntimeException("Neo4jRecordAdapter mapping failed for field: " + key, ex);
                }
            });
            return instance;
        } catch (Exception ex) {
            throw new RuntimeException("Neo4jRecordAdapter failed to adapt to " + targetType.getName(), ex);
        }
    }

    public <T> T adapt(UserNode node, Class<T> targetType) {
        if (node == null) return null;
        if (node.getUserId() == null)
            throw new IllegalArgumentException("UserNode has null userId, cannot adapt");
        Map<String, Object> record = Map.of(
                "userId", node.getUserId()
        );
        return adapt(record, targetType);
    }
}
