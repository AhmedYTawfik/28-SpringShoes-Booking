package com.team28.booking.calendar.service;

import com.team28.booking.calendar.adapter.CassandraRowAdapter;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEvent;
import com.team28.booking.calendar.cassandra.CalendarAvailabilityEventRepository;
import com.team28.booking.calendar.dto.AvailabilitySnapshotDTO;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityHistoryServiceTest {

    @Mock private CalendarAvailabilityEventRepository eventRepository;
    @Mock private TimeSlotRepository timeSlotRepository;

    private final CassandraRowAdapter cassandraRowAdapter = new CassandraRowAdapter();

    private AvailabilityHistoryService service;

    private static final Long PROVIDER_ID = 42L;
    private static final Instant T_1400 = Instant.parse("2026-05-02T14:00:00Z");
    private static final Instant T_1415 = Instant.parse("2026-05-02T14:15:00Z");
    private static final Instant T_1430 = Instant.parse("2026-05-02T14:30:00Z");

    @BeforeEach
    void setUp() {
        service = new AvailabilityHistoryService(eventRepository, timeSlotRepository, cassandraRowAdapter);
    }

    private CalendarAvailabilityEvent event(Instant ts, Double utilization) {
        return new CalendarAvailabilityEvent(PROVIDER_ID, ts, "2026-05-02", 10, 5, 5, utilization, "ok");
    }

    @Test
    void getAvailabilityHistory_noRange_returnsAllSnapshotsInDescOrder() {
        when(timeSlotRepository.countProviderById(PROVIDER_ID)).thenReturn(1L);
        when(eventRepository.findByProviderId(PROVIDER_ID))
                .thenReturn(List.of(event(T_1430, 0.9), event(T_1415, 0.6), event(T_1400, 0.3)));

        List<AvailabilitySnapshotDTO> result = service.getAvailabilityHistory(PROVIDER_ID, null, null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTimestamp()).isEqualTo(T_1430);
        assertThat(result.get(0).getUtilizationRate()).isEqualTo(0.9);
        assertThat(result.get(2).getTimestamp()).isEqualTo(T_1400);
        verify(eventRepository, never()).findByProviderIdAndTimestampBetween(any(), any(), any());
    }

    @Test
    void getAvailabilityHistory_withRange_callsRangeQuery() {
        Instant from = Instant.parse("2026-05-02T14:10:00Z");
        Instant to   = Instant.parse("2026-05-02T14:20:00Z");
        when(timeSlotRepository.countProviderById(PROVIDER_ID)).thenReturn(1L);
        when(eventRepository.findByProviderIdAndTimestampBetween(eq(PROVIDER_ID), eq(from), eq(to)))
                .thenReturn(List.of(event(T_1415, 0.6)));

        List<AvailabilitySnapshotDTO> result = service.getAvailabilityHistory(PROVIDER_ID, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTimestamp()).isEqualTo(T_1415);
        verify(eventRepository, never()).findByProviderId(any());
    }

    @Test
    void getAvailabilityHistory_providerNotFound_throws404() {
        when(timeSlotRepository.countProviderById(999L)).thenReturn(0L);

        assertThatThrownBy(() -> service.getAvailabilityHistory(999L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Provider not found");
        verify(eventRepository, never()).findByProviderId(any());
    }

    @Test
    void getAvailabilityHistory_providerExistsNoSnapshots_returnsEmptyList() {
        when(timeSlotRepository.countProviderById(PROVIDER_ID)).thenReturn(1L);
        when(eventRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of());

        List<AvailabilitySnapshotDTO> result = service.getAvailabilityHistory(PROVIDER_ID, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getAvailabilityHistory_adapterMapsAllFields() {
        when(timeSlotRepository.countProviderById(PROVIDER_ID)).thenReturn(1L);
        CalendarAvailabilityEvent row = new CalendarAvailabilityEvent(
                PROVIDER_ID, T_1430, "2026-05-02", 12, 4, 8, 0.6667, "peak hour");
        when(eventRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of(row));

        AvailabilitySnapshotDTO dto = service.getAvailabilityHistory(PROVIDER_ID, null, null).get(0);

        assertThat(dto.getProviderId()).isEqualTo(PROVIDER_ID);
        assertThat(dto.getTimestamp()).isEqualTo(T_1430);
        assertThat(dto.getDate()).isEqualTo("2026-05-02");
        assertThat(dto.getTotalSlots()).isEqualTo(12);
        assertThat(dto.getAvailableSlots()).isEqualTo(4);
        assertThat(dto.getBookedSlots()).isEqualTo(8);
        assertThat(dto.getUtilizationRate()).isEqualTo(0.6667);
        assertThat(dto.getNotes()).isEqualTo("peak hour");
    }
}