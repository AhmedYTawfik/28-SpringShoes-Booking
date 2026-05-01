package com.team28.booking.user.factory;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    @Test
    void mongoEventIsInterfaceWithMandatoryMethods() {
        assertTrue(MongoEvent.class.isInterface(), "MongoEvent must be an interface");
        try {
            MongoEvent.class.getDeclaredMethod("getId");
            MongoEvent.class.getDeclaredMethod("getTimestamp");
            MongoEvent.class.getDeclaredMethod("getAction");
            MongoEvent.class.getDeclaredMethod("getDetails");
        } catch (NoSuchMethodException e) {
            fail("MongoEvent is missing mandatory getter methods");
        }
    }

    @Test
    void eventFactoryIsComponentWithCreateEventMethod() {
        assertNotNull(EventFactory.class.getAnnotation(Component.class), "EventFactory must be a @Component");
        try {
            Method m = EventFactory.class.getDeclaredMethod("createEvent", EventType.class, Map.class);
            assertTrue(Modifier.isPublic(m.getModifiers()));
            assertEquals(MongoEvent.class, m.getReturnType());
        } catch (NoSuchMethodException e) {
            fail("EventFactory must have createEvent(EventType, Map) method");
        }
    }

    @Test
    void eventTypeEnumContainsAllRequiredValues() {
        assertTrue(EventType.class.isEnum());
        String[] required = {"AUTH", "PROVIDER", "BOOKING", "CALENDAR", "PAYMENT_AUDIT"};
        for (String r : required) {
            assertNotNull(EventType.valueOf(r), "EventType must contain " + r);
        }
    }
}
