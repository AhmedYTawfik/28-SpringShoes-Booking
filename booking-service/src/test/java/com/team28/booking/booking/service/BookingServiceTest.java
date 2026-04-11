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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // --- S3-F8: addServicesToBooking ---

    @Test
    void testAddServicesToBooking_RequestedWithNoServices_Success() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.team28.booking.booking.dto.AddServiceItemDTO s1 = new com.team28.booking.booking.dto.AddServiceItemDTO("Service A", 30, BigDecimal.valueOf(100), Map.of());
        com.team28.booking.booking.dto.AddServiceItemDTO s2 = new com.team28.booking.booking.dto.AddServiceItemDTO("Service B", 45, BigDecimal.valueOf(150), Map.of());

        Booking result = bookingService.addServicesToBooking(1L, List.of(s1, s2));

        assertEquals(2, result.getBookingServices().size());
        
        com.team28.booking.booking.model.BookingItem item1 = result.getBookingServices().get(0);
        assertEquals("Service A", item1.getServiceName());
        assertEquals(1, item1.getServiceOrder());
        assertEquals(com.team28.booking.booking.model.BookingItem.Status.PENDING, item1.getStatus());

        com.team28.booking.booking.model.BookingItem item2 = result.getBookingServices().get(1);
        assertEquals("Service B", item2.getServiceName());
        assertEquals(2, item2.getServiceOrder());
        assertEquals(com.team28.booking.booking.model.BookingItem.Status.PENDING, item2.getStatus());
        
        verify(bookingRepository).save(booking);
    }

    @Test
    void testAddServicesToBooking_AddServiceToExistingServices_Success() {
        booking.setStatus(Booking.Status.REQUESTED);
        
        com.team28.booking.booking.model.BookingItem existing1 = new com.team28.booking.booking.model.BookingItem();
        existing1.setServiceName("Old 1");
        existing1.setServiceOrder(1);
        
        com.team28.booking.booking.model.BookingItem existing2 = new com.team28.booking.booking.model.BookingItem();
        existing2.setServiceName("Old 2");
        existing2.setServiceOrder(2);
        
        booking.setBookingServices(new ArrayList<>(List.of(existing1, existing2)));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.team28.booking.booking.dto.AddServiceItemDTO s3 = new com.team28.booking.booking.dto.AddServiceItemDTO("New Service", 60, BigDecimal.valueOf(200), Map.of());

        Booking result = bookingService.addServicesToBooking(1L, List.of(s3));

        assertEquals(3, result.getBookingServices().size());
        com.team28.booking.booking.model.BookingItem item3 = result.getBookingServices().get(2);
        assertEquals("New Service", item3.getServiceName());
        assertEquals(3, item3.getServiceOrder());
        assertEquals(com.team28.booking.booking.model.BookingItem.Status.PENDING, item3.getStatus());
    }

    @Test
    void testAddServicesToBooking_CompletedStatus_Throws400() {
        booking.setStatus(Booking.Status.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        com.team28.booking.booking.dto.AddServiceItemDTO s = new com.team28.booking.booking.dto.AddServiceItemDTO("S", 10, BigDecimal.TEN, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of(s)));
        
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_MissingName_Throws400() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        com.team28.booking.booking.dto.AddServiceItemDTO invalidService = new com.team28.booking.booking.dto.AddServiceItemDTO("", 30, BigDecimal.TEN, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of(invalidService)));
        
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_InvalidDuration_Throws400() {
        booking.setStatus(Booking.Status.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        com.team28.booking.booking.dto.AddServiceItemDTO invalidService = new com.team28.booking.booking.dto.AddServiceItemDTO("Valid", 0, BigDecimal.TEN, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of(invalidService)));
        
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_InvalidPrice_Throws400() {
        booking.setStatus(Booking.Status.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        com.team28.booking.booking.dto.AddServiceItemDTO invalidService = new com.team28.booking.booking.dto.AddServiceItemDTO("Valid", 30, BigDecimal.ZERO, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of(invalidService)));
        
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }
}
