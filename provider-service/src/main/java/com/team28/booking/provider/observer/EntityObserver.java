package com.team28.booking.provider.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
