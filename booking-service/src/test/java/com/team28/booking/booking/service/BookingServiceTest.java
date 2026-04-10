package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingService bookingService;

    private Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setId(1L);
        booking.setProviderId(99L);
    }

    @Test
    void testCancelBooking_Requested_ProviderAssigned() {
        // Create a REQUESTED booking with a provider assigned
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // PUT /api/bookings/{id}/cancel
        Booking result = bookingService.cancelBooking(1L);

        // Assert booking status=CANCELLED
        assertEquals(Booking.Status.CANCELLED, result.getStatus());

        // Assert provider status=AVAILABLE method is called
        verify(bookingRepository, times(1)).updateProviderStatusToAvailable(99L);
    }

    @Test
    void testCancelBooking_Confirmed_ProviderAssigned() {
        // Create a Confirmed booking with a provider assigned
        booking.setStatus(Booking.Status.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // PUT /api/bookings/{id}/cancel
        Booking result = bookingService.cancelBooking(1L);

        // Assert booking status=CANCELLED
        assertEquals(Booking.Status.CANCELLED, result.getStatus());

        // Assert provider status=AVAILABLE method is called
        verify(bookingRepository, times(1)).updateProviderStatusToAvailable(99L);
    }

    @Test
    void testCancelBooking_Completed_Throws400() {
        // Try cancelling a COMPLETED booking -> 400
        booking.setStatus(Booking.Status.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            bookingService.cancelBooking(1L);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void testCancelBooking_InProgress_Throws400() {
        // Try cancelling an IN_PROGRESS booking -> 400
        booking.setStatus(Booking.Status.IN_PROGRESS);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            bookingService.cancelBooking(1L);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void testCancelBooking_Cancelled_Throws400() {
        // Try cancelling an already CANCELLED booking -> 400
        booking.setStatus(Booking.Status.CANCELLED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            bookingService.cancelBooking(1L);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
