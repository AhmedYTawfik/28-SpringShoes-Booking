package com.team28.booking.calendar.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based tests verifying the Builder pattern (DP-4) for CalendarAnalyticsDTO.
 */
class CalendarAnalyticsDTOBuilderTest {

    // ── (a) CalendarAnalyticsDTO.builder() is public static ──────────────────

    @Test
    void testBuilderMethod_existsAndIsPublicStatic() throws NoSuchMethodException {
        Method builderMethod = CalendarAnalyticsDTO.class.getDeclaredMethod("builder");
        assertThat(Modifier.isPublic(builderMethod.getModifiers()))
                .as("builder() must be public")
                .isTrue();
        assertThat(Modifier.isStatic(builderMethod.getModifiers()))
                .as("builder() must be static")
                .isTrue();
        assertThat(builderMethod.getReturnType())
                .as("builder() must return CalendarAnalyticsDTOBuilder")
                .isEqualTo(CalendarAnalyticsDTOBuilder.class);
    }

    // ── (b) Builder has fluent setters returning the Builder type ─────────────

    @Test
    void testBuilder_fluentSettersReturnBuilderType() throws Exception {
        // Verify each setter method returns CalendarAnalyticsDTOBuilder (fluent API)
        String[] setters = {"totalSlots", "availableSlots", "bookedSlots", "utilizationRate", "slotsByDate"};
        Class<?>[] paramTypes = {long.class, long.class, long.class, double.class, Map.class};

        for (int i = 0; i < setters.length; i++) {
            Method setter = CalendarAnalyticsDTOBuilder.class.getDeclaredMethod(setters[i], paramTypes[i]);
            assertThat(setter.getReturnType())
                    .as("setter %s must return CalendarAnalyticsDTOBuilder", setters[i])
                    .isEqualTo(CalendarAnalyticsDTOBuilder.class);
            assertThat(Modifier.isPublic(setter.getModifiers()))
                    .as("setter %s must be public", setters[i])
                    .isTrue();
        }
    }

    // ── (c) build() returns CalendarAnalyticsDTO ──────────────────────────────

    @Test
    void testBuilder_buildReturnsCorrectDTO() throws Exception {
        Method buildMethod = CalendarAnalyticsDTOBuilder.class.getDeclaredMethod("build");
        assertThat(buildMethod.getReturnType())
                .as("build() must return CalendarAnalyticsDTO")
                .isEqualTo(CalendarAnalyticsDTO.class);
        assertThat(Modifier.isPublic(buildMethod.getModifiers()))
                .as("build() must be public")
                .isTrue();

        // Functional check: build a real DTO and verify values round-trip correctly
        Map<String, Long> slotsByDate = Map.of("2026-04-15", 6L, "2026-04-16", 4L);
        CalendarAnalyticsDTO dto = CalendarAnalyticsDTO.builder()
                .totalSlots(10L)
                .availableSlots(4L)
                .bookedSlots(6L)
                .utilizationRate(0.6)
                .slotsByDate(slotsByDate)
                .build();

        assertThat(dto.totalSlots()).isEqualTo(10L);
        assertThat(dto.availableSlots()).isEqualTo(4L);
        assertThat(dto.bookedSlots()).isEqualTo(6L);
        assertThat(dto.utilizationRate()).isEqualTo(0.6);
        assertThat(dto.slotsByDate()).isEqualTo(slotsByDate);
    }
}
