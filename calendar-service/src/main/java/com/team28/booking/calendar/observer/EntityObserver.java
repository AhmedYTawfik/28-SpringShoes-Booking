package com.team28.booking.calendar.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
