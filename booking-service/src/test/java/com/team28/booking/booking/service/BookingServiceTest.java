package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.dto.BookingAnalyticsDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.neo4j.UserNodeRepository;
import com.team28.booking.booking.observer.MongoEventLogger;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MongoEventLogger mongoEventLogger;

    @Mock
    private CacheInvalidator cacheInvalidator;

    @Mock
    private UserNodeRepository userNodeRepository;

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

    // --- S3-F4: completeBooking ---

    @Test
    void completeBooking_inProgress_returnsCompleted() {
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setTotalPrice(BigDecimal.valueOf(200));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.completeBooking(1L);

        assertEquals(Booking.Status.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(bookingRepository).updateProviderStatusToAvailable(99L);
        verify(bookingRepository).createInvoiceForBooking(1L, null, BigDecimal.valueOf(200));
    }

    @Test
    void completeBooking_requestedStatus_throws400() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBooking_alreadyCompleted_throws400() {
        booking.setStatus(Booking.Status.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void completeBooking_notFound_throws404() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void completeBooking_nullTotalPrice_sumsFromServices() {
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setTotalPrice(null);
        BookingItem item1 = new BookingItem();
        item1.setPrice(BigDecimal.valueOf(100));
        item1.setServiceOrder(1);
        BookingItem item2 = new BookingItem();
        item2.setPrice(BigDecimal.valueOf(50));
        item2.setServiceOrder(2);
        booking.setBookingServices(new ArrayList<>(List.of(item1, item2)));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.completeBooking(1L);

        assertEquals(BigDecimal.valueOf(150), result.getTotalPrice());
        verify(bookingRepository).createInvoiceForBooking(1L, null, BigDecimal.valueOf(150));
    }

    @Test
    void completeBooking_nullProviderId_skipsProviderUpdate() {
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setProviderId(null);
        booking.setTotalPrice(BigDecimal.valueOf(100));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.completeBooking(1L);

        verify(bookingRepository, never()).updateProviderStatusToAvailable(anyLong());
    }

    // --- S3-F6: getAnalytics ---

    @Test
    void getAnalytics_validRange_returnsDTO() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end   = LocalDate.of(2026, 12, 31);
        Object[] row = new Object[]{5L, 3L, 1L, new BigDecimal("600.00"), new BigDecimal("200.00")};
        when(bookingRepository.getBookingAnalytics(any(), any())).thenReturn(row);

        BookingAnalyticsDTO dto = bookingService.getAnalytics(start, end);

        assertEquals(5L, dto.totalBookings());
        assertEquals(3L, dto.completedBookings());
        assertEquals(1L, dto.cancelledBookings());
        assertEquals(new BigDecimal("600.00"), dto.totalRevenue());
        assertEquals(60.0, dto.completionRate(), 0.001);
    }

    @Test
    void getAnalytics_startAfterEnd_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getAnalytics(
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).getBookingAnalytics(any(), any());
    }

    // --- S3-F9: getBookingDetails ---

    @Test
    void getBookingDetails_returnsOrderedServicesAndCounts() {
        Booking b = new Booking();
        b.setId(1L);
        b.setUserId(10L);
        b.setStatus(Booking.Status.IN_PROGRESS);
        b.setTotalPrice(BigDecimal.valueOf(150));

        BookingItem s1 = new BookingItem();
        s1.setId(1L); s1.setServiceOrder(1); s1.setServiceName("A");
        s1.setDuration(30); s1.setPrice(BigDecimal.valueOf(100));
        s1.setStatus(BookingItem.Status.COMPLETED);

        BookingItem s2 = new BookingItem();
        s2.setId(2L); s2.setServiceOrder(2); s2.setServiceName("B");
        s2.setDuration(30); s2.setPrice(BigDecimal.valueOf(50));
        s2.setStatus(BookingItem.Status.PENDING);

        b.setBookingServices(new ArrayList<>(List.of(s2, s1)));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        BookingDetailsDTO dto = bookingService.getBookingDetails(1L);

        assertEquals(2, dto.totalServices());
        assertEquals(1, dto.completedServices());
        assertEquals(1, dto.services().get(0).serviceOrder());
        assertEquals(2, dto.services().get(1).serviceOrder());
    }

    @Test
    void getBookingDetails_notFound_throws404() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getBookingDetails(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getBookingDetails_noServices_returnsZeroCounts() {
        Booking b = new Booking();
        b.setId(1L);
        b.setStatus(Booking.Status.REQUESTED);
        b.setBookingServices(new ArrayList<>());
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        BookingDetailsDTO dto = bookingService.getBookingDetails(1L);

        assertEquals(0, dto.totalServices());
        assertEquals(0, dto.completedServices());
        assertTrue(dto.services().isEmpty());
    }

    // --- S3-F1: searchBookings ---

    @Test
    void searchBookings_withStatus_delegatesToRepository() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end   = LocalDate.of(2026, 12, 31);
        Booking b = new Booking();
        b.setId(1L);
        b.setStatus(Booking.Status.CONFIRMED);
        when(bookingRepository.searchBookingsByStatusAndDate(eq("CONFIRMED"), any(), any()))
                .thenReturn(List.of(b));

        List<Booking> result = bookingService.searchBookings("CONFIRMED", start, end);

        assertEquals(1, result.size());
        verify(bookingRepository).searchBookingsByStatusAndDate(eq("CONFIRMED"), any(), any());
    }

    @Test
    void searchBookings_nullStatus_passesNullToRepository() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end   = LocalDate.of(2026, 12, 31);
        when(bookingRepository.searchBookingsByStatusAndDate(isNull(), any(), any()))
                .thenReturn(List.of());

        List<Booking> result = bookingService.searchBookings(null, start, end);

        assertTrue(result.isEmpty());
        verify(bookingRepository).searchBookingsByStatusAndDate(isNull(), any(), any());
    }
}
