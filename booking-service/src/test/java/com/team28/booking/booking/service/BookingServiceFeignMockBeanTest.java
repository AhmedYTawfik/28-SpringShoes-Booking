package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.messaging.BookingEventPublisher;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.observer.MongoEventLogger;
import com.team28.booking.booking.repository.BookingItemRepository;
import com.team28.booking.booking.repository.BookingRepository;
import com.team28.booking.contracts.dto.ProviderDTO;
import com.team28.booking.contracts.dto.TimeSlotDTO;
import com.team28.booking.contracts.dto.UserDTO;
import com.team28.booking.contracts.feign.CalendarServiceClient;
import com.team28.booking.contracts.feign.InvoiceServiceClient;
import com.team28.booking.contracts.feign.ProviderServiceClient;
import com.team28.booking.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookingService.class)
class BookingServiceFeignMockBeanTest {

    @Autowired
    private BookingService bookingService;

    @MockitoBean private BookingRepository bookingRepository;
    @MockitoBean private BookingItemRepository bookingItemRepository;
    @MockitoBean private MongoEventLogger mongoEventLogger;
    @MockitoBean private CacheInvalidator cacheInvalidator;
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private Neo4jClient neo4jClient;
    @MockitoBean private BookingEventPublisher eventPublisher;
    @MockitoBean private ProviderServiceClient providerServiceClient;
    @MockitoBean private InvoiceServiceClient invoiceServiceClient;
    @MockitoBean private UserServiceClient userServiceClient;
    @MockitoBean private CalendarServiceClient calendarServiceClient;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completeBooking_usesMockedFeignPreChecksAndPublishesSagaEvent() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Booking booking = new Booking();
        booking.setId(42L);
        booking.setUserId(7L);
        booking.setProviderId(9L);
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setTotalPrice(new BigDecimal("125.00"));
        booking.setAppointmentDate(LocalDate.of(2026, 5, 17));
        booking.setStartTime(LocalTime.of(10, 0));

        when(bookingRepository.findById(42L)).thenReturn(Optional.of(booking));
        when(userServiceClient.getUser(7L)).thenReturn(new UserDTO(7L, "User", "u@test.com", "USER", "ACTIVE", null, Map.of(), null));
        when(providerServiceClient.getProvider(9L)).thenReturn(new ProviderDTO(9L, 7L, "Provider", "Shoes", "BUSY", 5.0, 1, BigDecimal.TEN, Map.of()));
        when(calendarServiceClient.getSlotForBooking(9L, "2026-05-17", "10:00")).thenReturn(
                new TimeSlotDTO(3L, 9L, LocalDate.of(2026, 5, 17), LocalTime.of(10, 0), LocalTime.of(11, 0), false, Map.of()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(42L);

        assertThat(result.getStatus()).isEqualTo(Booking.Status.COMPLETING);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(userServiceClient).getUser(7L);
        verify(providerServiceClient).getProvider(9L);
        verify(calendarServiceClient).getSlotForBooking(9L, "2026-05-17", "10:00");
        verify(eventPublisher).publishBookingCompleted(42L, 7L, 9L, 125.0);
    }
}
