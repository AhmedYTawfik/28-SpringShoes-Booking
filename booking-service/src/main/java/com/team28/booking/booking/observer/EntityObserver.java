package com.team28.booking.booking.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
