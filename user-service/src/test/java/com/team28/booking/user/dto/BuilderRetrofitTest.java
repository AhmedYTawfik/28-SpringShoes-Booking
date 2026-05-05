package com.team28.booking.user.dto;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class BuilderRetrofitTest {

    @Test
    void userBookingSummaryDTOHasBuilder() throws Exception {
        assertDtoHasBuilder(UserBookingSummaryDTO.class);
    }

    @Test
    void topClientDTOHasBuilder() throws Exception {
        assertDtoHasBuilder(TopClientDTO.class);
    }

    @Test
    void userProfileDTOHasBuilder() throws Exception {
        assertDtoHasBuilder(UserProfileDTO.class);
    }

    private void assertDtoHasBuilder(Class<?> dtoClass) throws Exception {
        Method builderMethod = dtoClass.getDeclaredMethod("builder");
        assertTrue(Modifier.isStatic(builderMethod.getModifiers()), "builder() must be static");
        
        Class<?> builderClass = builderMethod.getReturnType();
        assertTrue(builderClass.getSimpleName().endsWith("Builder"), "Return type must be a Builder class");
        
        Method buildMethod = builderClass.getDeclaredMethod("build");
        assertEquals(dtoClass, buildMethod.getReturnType(), "build() must return the DTO class");
    }
}
