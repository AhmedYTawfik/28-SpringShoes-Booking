package com.team28.booking.user.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class AdapterRetrofitTest {

    @Test
    void mongoDocumentAdapterExistsAndIsComponent() {
        assertNotNull(MongoDocumentAdapter.class.getAnnotation(Component.class));
        try {
            Method m = MongoDocumentAdapter.class.getDeclaredMethod("adapt", Object.class);
            assertTrue(Modifier.isPublic(m.getModifiers()));
        } catch (NoSuchMethodException e) {
            // Check if there's a generic adapt method or specific one
            Method[] methods = MongoDocumentAdapter.class.getDeclaredMethods();
            boolean found = false;
            for (Method m : methods) {
                if (m.getName().equals("adapt") && Modifier.isPublic(m.getModifiers())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "MongoDocumentAdapter must have a public adapt method");
        }
    }

    @Test
    void objectArrayDtoAdapterExistsAndIsComponent() {
        assertNotNull(ObjectArrayDtoAdapter.class.getAnnotation(Component.class));
        // S1-F3 specifically requires ObjectArrayDtoAdapter for UserBookingSummaryDTO
        try {
            Method m = ObjectArrayDtoAdapter.class.getDeclaredMethod("toUserBookingSummaryDTO", Object[].class);
            assertTrue(Modifier.isPublic(m.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("ObjectArrayDtoAdapter must have toUserBookingSummaryDTO(Object[]) method for S1-F3");
        }
    }
}
