package com.team28.booking.calendar.service;

import com.team28.booking.calendar.cassandra.CalendarAvailabilityEvent;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEventRepository;
import com.team28.booking.calendar.dto.AvailabilitySnapshotRequest;
import com.team28.booking.calendar.observer.EntityObserver;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for S4-F11: Record Provider Availability Snapshot.
 * Uses Mockito stubs — no live Cassandra or PostgreSQL required.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilitySnapshotServiceTest {

    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private CalendarAvailabilityEventRepository cassandraRepo;
    @Mock private com.team28.booking.calendar.cache.CacheInvalidator cacheInvalidator;
    @Mock private EntityObserver observer;

    private TimeSlotService timeSlotService;

    private static final Long PROVIDER_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 4, 22);

    @BeforeEach
    void setUp() {
        timeSlotService = new TimeSlotService(
                timeSlotRepository,
                null,         // MongoEventLogger — replaced by mock observer below
                null,         // ObjectArrayDtoAdapter
                cacheInvalidator,
                cassandraRepo,
                null          // ProviderServiceClient — not needed for snapshot tests
        );
        // Register the mock observer so we can verify it gets called
        timeSlotService.register(observer);
    }

    // ── Helper builders ──────────────────────────────────────────────────────

    private Object[] statsRow(int total, int available, int booked) {
        return new Object[]{BigInteger.valueOf(total), BigInteger.valueOf(available), BigInteger.valueOf(booked)};
    }

    private AvailabilitySnapshotRequest request(LocalDate date, String notes) {
        return new AvailabilitySnapshotRequest(date, notes);
    }

    // ── (a) Normal: 10 slots (4 avail, 6 booked) ─────────────────────────────

    @Test
    void testRecordSnapshot_normalCase_201() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(10, 4, 6)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, "test notes"));

        // Cassandra save must have been called once
        ArgumentCaptor<CalendarAvailabilityEvent> captor =
                ArgumentCaptor.forClass(CalendarAvailabilityEvent.class);
        verify(cassandraRepo, times(1)).save(captor.capture());

        CalendarAvailabilityEvent saved = captor.getValue();
        assertThat(saved.getProviderId()).isEqualTo(PROVIDER_ID);
        assertThat(saved.getTotalSlots()).isEqualTo(10);
        assertThat(saved.getAvailableSlots()).isEqualTo(4);
        assertThat(saved.getBookedSlots()).isEqualTo(6);
        assertThat(saved.getUtilizationRate()).isEqualTo(0.6);
        assertThat(saved.getNotes()).isEqualTo("test notes");
    }

    // ── (b) 0 slots on that date ─────────────────────────────────────────────

    @Test
    void testRecordSnapshot_zeroSlots_savedWithZeros() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(0, 0, 0)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null));

        ArgumentCaptor<CalendarAvailabilityEvent> captor =
                ArgumentCaptor.forClass(CalendarAvailabilityEvent.class);
        verify(cassandraRepo, times(1)).save(captor.capture());

        CalendarAvailabilityEvent saved = captor.getValue();
        assertThat(saved.getTotalSlots()).isZero();
        assertThat(saved.getAvailableSlots()).isZero();
        assertThat(saved.getBookedSlots()).isZero();
        assertThat(saved.getUtilizationRate()).isZero();
    }

    // ── (c) Two snapshots 5 minutes apart → Cassandra save called twice ───────

    @Test
    void testRecordSnapshot_twoSnapshots_cassandraSavedTwice() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(5, 2, 3)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null));
        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null));

        verify(cassandraRepo, times(2)).save(any(CalendarAvailabilityEvent.class));
    }

    // ── (d) Notes field is null → snapshot saved with null notes ─────────────

    @Test
    void testRecordSnapshot_nullNotes_savedWithNullNotes() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(3, 1, 2)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null));

        ArgumentCaptor<CalendarAvailabilityEvent> captor =
                ArgumentCaptor.forClass(CalendarAvailabilityEvent.class);
        verify(cassandraRepo).save(captor.capture());
        assertThat(captor.getValue().getNotes()).isNull();
    }

    // ── (e) Notes field is present → snapshot saved with notes value ──────────

    @Test
    void testRecordSnapshot_notesPresent_savedWithNotes() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(4, 2, 2)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, "Morning block fully booked"));

        ArgumentCaptor<CalendarAvailabilityEvent> captor =
                ArgumentCaptor.forClass(CalendarAvailabilityEvent.class);
        verify(cassandraRepo).save(captor.capture());
        assertThat(captor.getValue().getNotes()).isEqualTo("Morning block fully booked");
    }

    // ── (f) Observer called with correct payload ──────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void testRecordSnapshot_observerCalledWithCorrectPayload() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(6, 2, 4)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(observer, atLeastOnce()).onEvent(eq("TRACKING_RECORDED"), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("providerId", PROVIDER_ID)
                           .containsEntry("date", DATE.toString())
                           .containsEntry("totalSlots", 6)
                           .containsEntry("availableSlots", 2)
                           .containsEntry("bookedSlots", 4);
        assertThat((Double) payload.get("utilizationRate"))
                .isEqualTo(4.0 / 6.0);
    }

    // ── (g) Cassandra save fails → exception propagates ──────────────────────

    @Test
    void testRecordSnapshot_cassandraFails_exceptionPropagates() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(5, 2, 3)});
        doThrow(new RuntimeException("Cassandra unavailable"))
                .when(cassandraRepo).save(any());

        assertThatThrownBy(() ->
                timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cassandra unavailable");
    }

    // ── (h) Cache invalidation keys verified ─────────────────────────────────

    @Test
    void testRecordSnapshot_cacheInvalidationCalled() {
        when(timeSlotRepository.getSnapshotStats(PROVIDER_ID, DATE))
                .thenReturn(new Object[]{statsRow(3, 1, 2)});

        timeSlotService.recordAvailabilitySnapshot(PROVIDER_ID, request(DATE, null));

        verify(cacheInvalidator).wildcardDelete("calendar-service::S4-F12::" + PROVIDER_ID);
        verify(cacheInvalidator).wildcardDelete("calendar-service::S4-F10::*");
    }
}
