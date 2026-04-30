package com.team28.booking.invoice.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
