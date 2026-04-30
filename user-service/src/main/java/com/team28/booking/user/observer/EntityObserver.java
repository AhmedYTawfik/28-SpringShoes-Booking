package com.team28.booking.user.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
