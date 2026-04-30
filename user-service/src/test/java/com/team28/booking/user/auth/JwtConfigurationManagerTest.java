package com.team28.booking.user.auth;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtConfigurationManagerTest {

    @Test
    void singletonHasOnePrivateConstructorAndStaticAccessor() throws NoSuchMethodException {
        Constructor<?>[] constructors = JwtConfigurationManager.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));

        Method method = JwtConfigurationManager.class.getDeclaredMethod("getInstance");
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(JwtConfigurationManager.class, method.getReturnType());
    }

    @Test
    void repeatedCallsReturnTheSameReference() {
        assertSame(JwtConfigurationManager.getInstance(), JwtConfigurationManager.getInstance());
    }

    @Test
    void concurrentCallsReturnTheSameReference() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Future<JwtConfigurationManager>> futures = IntStream.range(0, 10)
                    .mapToObj(i -> executor.submit(JwtConfigurationManager::getInstance))
                    .toList();

            JwtConfigurationManager first = futures.get(0).get();
            for (Future<JwtConfigurationManager> future : futures) {
                assertSame(first, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void singletonIsNotAnnotatedAsASpringBean() {
        for (var annotation : JwtConfigurationManager.class.getAnnotations()) {
            assertFalse(annotation.annotationType().getName().startsWith("org.springframework"));
        }
    }
}
