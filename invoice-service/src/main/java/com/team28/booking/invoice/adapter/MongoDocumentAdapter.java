package com.team28.booking.invoice.adapter;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DP-7 Adapter: converts a raw MongoDB document map to a target DTO type.
 * Exposes adapt(Map, Class<T>) so callers never instantiate target DTOs directly.
 */
@Component
public class MongoDocumentAdapter {

    public <T> T adapt(Map<String, Object> doc, Class<T> targetType) {
        if (doc == null) return null;
        try {
            T instance = targetType.getDeclaredConstructor().newInstance();
            doc.forEach((key, value) -> {
                try {
                    var field = targetType.getDeclaredField(key);
                    field.setAccessible(true);
                    field.set(instance, value);
                } catch (NoSuchFieldException ignored) {
                    // field not present in target DTO — skip
                } catch (Exception ex) {
                    throw new RuntimeException("MongoDocumentAdapter mapping failed for field: " + key, ex);
                }
            });
            return instance;
        } catch (Exception ex) {
            throw new RuntimeException("MongoDocumentAdapter failed to adapt to " + targetType.getName(), ex);
        }
    }
}
