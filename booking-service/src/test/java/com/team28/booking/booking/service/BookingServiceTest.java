package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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
    void testCompleteBooking_InProgress_Success() {
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setUserId(1L);
        booking.setTotalPrice(BigDecimal.valueOf(100));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(1L);

        assertEquals(Booking.Status.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(bookingRepository).updateProviderStatusToAvailable(99L);
        // Use a captor instead of eq() so the assertion is scale-insensitive (100 == 100.00)
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(bookingRepository).createInvoiceForBooking(eq(1L), eq(1L), amountCaptor.capture());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(amountCaptor.getValue()));
    }

    @Test
    void testCompleteBooking_NoProvider_InvoiceStillCreated() {
        // When providerId is null, provider status update must be skipped but invoice must still be created.
        booking.setProviderId(null);
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setUserId(1L);
        booking.setTotalPrice(BigDecimal.valueOf(50));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(1L);

        assertEquals(Booking.Status.COMPLETED, result.getStatus());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository).createInvoiceForBooking(eq(1L), eq(1L), any(BigDecimal.class));
    }

    @Test
    void testCompleteBooking_TotalPriceNullCalculatedFromItems() {
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setUserId(1L);
        booking.setTotalPrice(null);
        BookingItem item = new BookingItem();
        item.setPrice(BigDecimal.valueOf(75));
        booking.setBookingServices(List.of(item));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(1L);

        assertEquals(BigDecimal.valueOf(75), result.getTotalPrice());
    }

    @Test
    void testCompleteBooking_NotInProgress_Throws400() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        // Nothing should be written when the guard rejects the request
        verify(bookingRepository, never()).save(any());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository, never()).createInvoiceForBooking(any(), any(), any());
    }

    @Test
    void testCompleteBooking_AlreadyCompleted_Throws400() {
        booking.setStatus(Booking.Status.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository, never()).createInvoiceForBooking(any(), any(), any());
    }

    @Test
    void testCompleteBooking_NotFound_Throws404() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
        verify(bookingRepository, never()).createInvoiceForBooking(any(), any(), any());
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

    // --- S3-F5: searchByMetadata ---

    @Test
    void searchByMetadata_validKeyValue_returnsList() {
        Booking b1 = new Booking();
        b1.setId(2L);
        Booking b2 = new Booking();
        b2.setId(3L);
        when(bookingRepository.findByMetadataKeyValue("bookingType", "IN_PERSON"))
                .thenReturn(List.of(b1, b2));

        List<Booking> result = bookingService.searchByMetadata("bookingType", "IN_PERSON");

        assertEquals(2, result.size());
        verify(bookingRepository).findByMetadataKeyValue("bookingType", "IN_PERSON");
    }

    @Test
    void searchByMetadata_noMatches_returnsEmptyList() {
        when(bookingRepository.findByMetadataKeyValue("bookingType", "VIRTUAL"))
                .thenReturn(List.of());

        List<Booking> result = bookingService.searchByMetadata("bookingType", "VIRTUAL");

        assertTrue(result.isEmpty());
    }

    @Test
    void searchByMetadata_blankKey_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchByMetadata("  ", "IN_PERSON"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).findByMetadataKeyValue(any(), any());
    }

    @Test
    void searchByMetadata_emptyKey_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchByMetadata("", "IN_PERSON"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).findByMetadataKeyValue(any(), any());
    }

    @Test
    void searchByMetadata_blankValue_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchByMetadata("bookingType", "  "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).findByMetadataKeyValue(any(), any());
    }

    @Test
    void searchByMetadata_emptyValue_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchByMetadata("bookingType", ""));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).findByMetadataKeyValue(any(), any());
    }

    @Test
    void searchByMetadata_nullKey_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchByMetadata(null, "IN_PERSON"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).findByMetadataKeyValue(any(), any());
    }

    @Test
    void searchByMetadata_nullValue_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchByMetadata("bookingType", null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).findByMetadataKeyValue(any(), any());
    }
}
