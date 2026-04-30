package com.team28.booking.provider.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class Observable {

    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    protected void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            try {
                observer.onEvent(eventType, payload);
            } catch (Exception ex) {
                // intentional: swallow per §3.3 failure policy — PG tx must not roll back on Mongo failure
            }
        }
    }
}
