package com.team28.booking.calendar.service;

import com.team28.booking.calendar.dto.IdleProviderDTO;
import com.team28.booking.calendar.repository.TimeSlotRepository;
import com.team28.booking.contracts.dto.ProviderDTO;
import com.team28.booking.contracts.feign.ProviderServiceClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceIdleProvidersTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private ProviderServiceClient providerServiceClient;

    private TimeSlotService timeSlotService;

    @BeforeEach
    void setUp() {
        timeSlotService = new TimeSlotService(
                timeSlotRepository,
                null,
                null,
                null,
                null,
                providerServiceClient
        );
    }

    @Test
    void findIdleProviders_enrichesLocalAggregateWithProviderDetails() {
        when(timeSlotRepository.findIdleProviderIds(eq(2), any(LocalDate.class)))
                .thenReturn(List.of(row(7L, 1L, 5L), row(9L, 0L, 3L)));
        when(providerServiceClient.getProvider(7L))
                .thenReturn(provider(7L, "Ahmed Plumbing", "Plumbing", 4.8));
        when(providerServiceClient.getProvider(9L))
                .thenReturn(provider(9L, "Cairo Cleaning", "Cleaning", 4.4));

        List<IdleProviderDTO> result = timeSlotService.findIdleProviders(2, 30);

        assertThat(result).containsExactly(
                new IdleProviderDTO(7L, "Ahmed Plumbing", "Plumbing", 4.8, 1L, 5L),
                new IdleProviderDTO(9L, "Cairo Cleaning", "Cleaning", 4.4, 0L, 3L)
        );
    }

    @Test
    void findIdleProviders_usesSinceDaysToBuildLocalQueryDate() {
        when(timeSlotRepository.findIdleProviderIds(eq(1), any(LocalDate.class)))
                .thenReturn(List.of());

        timeSlotService.findIdleProviders(1, 14);

        ArgumentCaptor<LocalDate> sinceDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(timeSlotRepository).findIdleProviderIds(eq(1), sinceDateCaptor.capture());
        assertThat(sinceDateCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(14));
    }

    @Test
    void findIdleProviders_skipsProviderWhenFeignReturns404() {
        when(timeSlotRepository.findIdleProviderIds(eq(2), any(LocalDate.class)))
                .thenReturn(List.of(row(7L, 1L, 5L), row(99L, 0L, 2L)));
        when(providerServiceClient.getProvider(7L))
                .thenReturn(provider(7L, "Ahmed Plumbing", "Plumbing", 4.8));
        when(providerServiceClient.getProvider(99L))
                .thenThrow(new FeignException.NotFound("not found", request(), null, Map.of()));

        List<IdleProviderDTO> result = timeSlotService.findIdleProviders(2, 30);

        assertThat(result).containsExactly(
                new IdleProviderDTO(7L, "Ahmed Plumbing", "Plumbing", 4.8, 1L, 5L)
        );
    }

    @Test
    void findIdleProviders_returnsPartialResultsWhenProviderServiceFails() {
        when(timeSlotRepository.findIdleProviderIds(eq(2), any(LocalDate.class)))
                .thenReturn(List.of(row(7L, 1L, 5L), row(9L, 0L, 3L)));
        when(providerServiceClient.getProvider(7L))
                .thenReturn(provider(7L, "Ahmed Plumbing", "Plumbing", 4.8));
        when(providerServiceClient.getProvider(9L))
                .thenThrow(FeignException.errorStatus("getProvider", response(503)));

        List<IdleProviderDTO> result = timeSlotService.findIdleProviders(2, 30);

        assertThat(result).containsExactly(
                new IdleProviderDTO(7L, "Ahmed Plumbing", "Plumbing", 4.8, 1L, 5L)
        );
    }

    @Test
    void findIdleProviders_rejectsNegativeInputs() {
        assertThatThrownBy(() -> timeSlotService.findIdleProviders(-1, 30))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("maxBookedSlots");

        assertThatThrownBy(() -> timeSlotService.findIdleProviders(1, -1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sinceDays");
    }

    private Object[] row(long providerId, long bookedSlotsCount, long totalSlotsCount) {
        return new Object[]{
                BigInteger.valueOf(providerId),
                BigInteger.valueOf(bookedSlotsCount),
                BigInteger.valueOf(totalSlotsCount)
        };
    }

    private ProviderDTO provider(Long id, String name, String specialty, Double rating) {
        return new ProviderDTO(id, 100L + id, name, specialty, "ACTIVE", rating, 10, BigDecimal.TEN, Map.of());
    }

    private Request request() {
        return Request.create(Request.HttpMethod.GET, "/api/providers/99", Map.of(), null, StandardCharsets.UTF_8);
    }

    private Response response(int status) {
        return Response.builder()
                .status(status)
                .reason("provider-service error")
                .request(request())
                .build();
    }
}
