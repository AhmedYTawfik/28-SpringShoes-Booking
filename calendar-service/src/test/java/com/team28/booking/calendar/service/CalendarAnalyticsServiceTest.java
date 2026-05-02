package com.team28.booking.calendar.service;

import com.team28.booking.calendar.dto.CalendarAnalyticsDTO;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for S4-F10: Calendar Analytics Dashboard.
 * Uses Mockito stubs — no live database required.
 */
@ExtendWith(MockitoExtension.class)
class CalendarAnalyticsServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    // We test getCalendarAnalytics directly on TimeSlotService (the logic lives there);
    // CalendarAnalyticsService is just a @Cacheable wrapper.
    private TimeSlotService timeSlotService;

    @BeforeEach
    void setUp() {
        // Minimal constructor — we only need the repo for analytics tests
        timeSlotService = new TimeSlotService(
                timeSlotRepository,
                null,  // MongoEventLogger — not needed for read-only analytics
                null,  // ObjectArrayDtoAdapter
                null   // CacheInvalidator
        );
    }

    // ── Helper to build the raw Object[] rows that native queries return ──────

    private Object[] statsRow(long total, long available, long booked) {
        return new Object[]{BigInteger.valueOf(total), BigInteger.valueOf(available), BigInteger.valueOf(booked)};
    }

    private Object[] dateRow(String date, long count) {
        return new Object[]{date, BigInteger.valueOf(count)};
    }

    // ── (a) Normal case: 10 slots, 6 booked, 4 available ─────────────────────

    @Test
    void testAnalytics_normalCase() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 4, 30);

        when(timeSlotRepository.getAnalyticsStats(start, end))
                .thenReturn(new Object[]{statsRow(10, 4, 6)});
        List<Object[]> slotsByDateRows = new ArrayList<>();
        slotsByDateRows.add(dateRow("2026-04-15", 6L));
        slotsByDateRows.add(dateRow("2026-04-16", 4L));
        when(timeSlotRepository.getSlotsByDate(start, end)).thenReturn(slotsByDateRows);

        CalendarAnalyticsDTO dto = timeSlotService.getCalendarAnalytics(start, end);

        assertThat(dto.totalSlots()).isEqualTo(10);
        assertThat(dto.availableSlots()).isEqualTo(4);
        assertThat(dto.bookedSlots()).isEqualTo(6);
        assertThat(dto.utilizationRate()).isEqualTo(0.6);
        assertThat(dto.slotsByDate()).containsEntry("2026-04-15", 6L)
                                     .containsEntry("2026-04-16", 4L);
    }

    // ── (b) No slots in range → zeros, empty map ──────────────────────────────

    @Test
    void testAnalytics_noSlotsInRange() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end   = LocalDate.of(2026, 3, 31);

        when(timeSlotRepository.getAnalyticsStats(start, end))
                .thenReturn(new Object[]{statsRow(0, 0, 0)});
        when(timeSlotRepository.getSlotsByDate(start, end))
                .thenReturn(new ArrayList<>());

        CalendarAnalyticsDTO dto = timeSlotService.getCalendarAnalytics(start, end);

        assertThat(dto.totalSlots()).isZero();
        assertThat(dto.availableSlots()).isZero();
        assertThat(dto.bookedSlots()).isZero();
        assertThat(dto.utilizationRate()).isZero();
        assertThat(dto.slotsByDate()).isEmpty();
    }

    // ── (c) startDate after endDate → 400 Bad Request ─────────────────────────

    @Test
    void testAnalytics_invalidDateRange_throws400() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end   = LocalDate.of(2026, 4, 1);

        assertThatThrownBy(() -> timeSlotService.getCalendarAnalytics(start, end))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("startDate must be before or equal to endDate");
    }

    // ── (d) Same startDate and endDate (single day) ───────────────────────────

    @Test
    void testAnalytics_singleDay() {
        LocalDate day = LocalDate.of(2026, 4, 15);

        when(timeSlotRepository.getAnalyticsStats(day, day))
                .thenReturn(new Object[]{statsRow(3, 1, 2)});
        List<Object[]> singleDayRows = new ArrayList<>();
        singleDayRows.add(dateRow("2026-04-15", 3L));
        when(timeSlotRepository.getSlotsByDate(day, day)).thenReturn(singleDayRows);

        CalendarAnalyticsDTO dto = timeSlotService.getCalendarAnalytics(day, day);

        assertThat(dto.totalSlots()).isEqualTo(3);
        assertThat(dto.slotsByDate()).hasSize(1).containsKey("2026-04-15");
    }

    // ── (e) Large date range with multiple dates ───────────────────────────────

    @Test
    void testAnalytics_multipleDates_slotsByDateCorrect() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 4, 30);

        when(timeSlotRepository.getAnalyticsStats(start, end))
                .thenReturn(new Object[]{statsRow(15, 5, 10)});
        List<Object[]> multiDateRows = new ArrayList<>();
        multiDateRows.add(dateRow("2026-04-01", 3L));
        multiDateRows.add(dateRow("2026-04-10", 5L));
        multiDateRows.add(dateRow("2026-04-20", 7L));
        when(timeSlotRepository.getSlotsByDate(start, end)).thenReturn(multiDateRows);

        CalendarAnalyticsDTO dto = timeSlotService.getCalendarAnalytics(start, end);

        assertThat(dto.slotsByDate()).hasSize(3)
                .containsEntry("2026-04-01", 3L)
                .containsEntry("2026-04-10", 5L)
                .containsEntry("2026-04-20", 7L);
    }

    // ── (f) Only available slots (none booked) → utilizationRate = 0.0 ─────────

    @Test
    void testAnalytics_allAvailable_utilizationZero() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 4, 30);

        when(timeSlotRepository.getAnalyticsStats(start, end))
                .thenReturn(new Object[]{statsRow(5, 5, 0)});
        when(timeSlotRepository.getSlotsByDate(any(), any()))
                .thenReturn(List.of());

        CalendarAnalyticsDTO dto = timeSlotService.getCalendarAnalytics(start, end);

        assertThat(dto.bookedSlots()).isZero();
        assertThat(dto.utilizationRate()).isEqualTo(0.0);
    }

    // ── (g) Only booked slots (none available) → utilizationRate = 1.0 ─────────

    @Test
    void testAnalytics_allBooked_utilizationOne() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 4, 30);

        when(timeSlotRepository.getAnalyticsStats(start, end))
                .thenReturn(new Object[]{statsRow(8, 0, 8)});
        when(timeSlotRepository.getSlotsByDate(any(), any()))
                .thenReturn(List.of());

        CalendarAnalyticsDTO dto = timeSlotService.getCalendarAnalytics(start, end);

        assertThat(dto.availableSlots()).isZero();
        assertThat(dto.utilizationRate()).isEqualTo(1.0);
    }
}
