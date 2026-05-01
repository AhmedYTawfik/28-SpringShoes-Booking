package com.team28.booking.user.observer;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ObserverContractTest {

    @Test
    void entityObserverIsInterfaceWithOnEventMethod() {
        assertTrue(EntityObserver.class.isInterface(), "EntityObserver must be an interface");
        try {
            Method m = EntityObserver.class.getDeclaredMethod("onEvent", String.class, Object.class);
            assertTrue(Modifier.isPublic(m.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("EntityObserver must have onEvent(String, Object) method");
        }
    }

    @Test
    void mongoEventLoggerImplementsEntityObserverAndIsComponent() {
        assertTrue(EntityObserver.class.isAssignableFrom(MongoEventLogger.class), 
            "MongoEventLogger must implement EntityObserver");
        assertNotNull(MongoEventLogger.class.getAnnotation(Component.class), 
            "MongoEventLogger must be a @Component");
    }

    @Test
    void observableIsAbstractClassWithMandatoryMethods() {
        assertTrue(Modifier.isAbstract(Observable.class.getModifiers()), "Observable must be abstract");
        try {
            Observable.class.getDeclaredMethod("register", EntityObserver.class);
            Observable.class.getDeclaredMethod("unregister", EntityObserver.class);
            Method m = Observable.class.getDeclaredMethod("notifyObservers", String.class, Object.class);
            assertTrue(Modifier.isProtected(m.getModifiers()), "notifyObservers must be protected");
        } catch (NoSuchMethodException e) {
            fail("Observable is missing mandatory methods");
        }
    }
}
