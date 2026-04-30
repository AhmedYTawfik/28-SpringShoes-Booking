package com.team28.booking.calendar.adapter;

import com.team28.booking.calendar.cassandra.CalendarAvailabilityEvent;
import org.springframework.stereotype.Component;

/**
 * DP-7 Adapter: converts a CalendarAvailabilityEvent Cassandra row to a target DTO.
 * Used by S4-F12 availability history reads to decouple Cassandra rows from DTOs.
 */
@Component
public class CassandraRowAdapter {

    public <T> T adapt(CalendarAvailabilityEvent row, Class<T> targetType) {
        if (row == null) return null;
        try {
            T instance = targetType.getDeclaredConstructor().newInstance();
            mapField(instance, targetType, "providerId",     row.getProviderId());
            mapField(instance, targetType, "timestamp",      row.getTimestamp());
            mapField(instance, targetType, "date",           row.getDate());
            mapField(instance, targetType, "totalSlots",     row.getTotalSlots());
            mapField(instance, targetType, "availableSlots", row.getAvailableSlots());
            mapField(instance, targetType, "bookedSlots",    row.getBookedSlots());
            mapField(instance, targetType, "utilizationRate",row.getUtilizationRate());
            mapField(instance, targetType, "notes",          row.getNotes());
            return instance;
        } catch (Exception ex) {
            throw new RuntimeException("CassandraRowAdapter failed to adapt to " + targetType.getName(), ex);
        }
    }

    private <T> void mapField(T instance, Class<T> type, String fieldName, Object value) {
        try {
            var field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (NoSuchFieldException ignored) {
        } catch (Exception ex) {
            throw new RuntimeException("CassandraRowAdapter mapping failed for field: " + fieldName, ex);
        }
    }
}
