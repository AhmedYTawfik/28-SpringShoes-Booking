package com.team28.booking.booking.service;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.dto.BookingDetailsDTO;
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
        verify(bookingRepository).updateProviderStatus(99L, "AVAILABLE");
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
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
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
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
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
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
        verify(bookingRepository, never()).createInvoiceForBooking(any(), any(), any());
    }

    @Test
    void testCompleteBooking_NotFound_Throws404() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
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
        verify(bookingRepository, times(1)).updateProviderStatus(99L, "AVAILABLE");
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
        verify(bookingRepository, times(1)).updateProviderStatus(99L, "AVAILABLE");
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
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
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
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
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
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("AVAILABLE"));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void testAssignProvider_HappyPath_AssignsAndMarksProviderBusy() {
        booking.setStatus(Booking.Status.REQUESTED);
        booking.setProviderId(null);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.countProvidersById(7L)).thenReturn(1L);
        when(bookingRepository.findProviderStatusById(7L)).thenReturn("AVAILABLE");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.assignProvider(1L, 7L);

        assertEquals(Booking.Status.CONFIRMED, result.getStatus());
        assertEquals(7L, result.getProviderId());
        verify(bookingRepository).updateProviderStatus(7L, "BUSY");
    }

    @Test
    void testAssignProvider_BookingNotRequested_Throws400() {
        booking.setStatus(Booking.Status.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.assignProvider(1L, 7L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(bookingRepository, never()).countProvidersById(anyLong());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("BUSY"));
    }

    @Test
    void testAssignProvider_ProviderNotFound_Throws404() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.countProvidersById(999L)).thenReturn(0L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.assignProvider(1L, 999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Provider not found", ex.getReason());
        verify(bookingRepository, never()).findProviderStatusById(anyLong());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("BUSY"));
    }

    @Test
    void testAssignProvider_ProviderNotAvailable_Throws400() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.countProvidersById(7L)).thenReturn(1L);
        when(bookingRepository.findProviderStatusById(7L)).thenReturn("BUSY");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.assignProvider(1L, 7L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Provider is not available", ex.getReason());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("BUSY"));
    }

    @Test
    void testAssignProvider_BookingNotFound_Throws404() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.assignProvider(404L, 7L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(bookingRepository, never()).countProvidersById(anyLong());
        verify(bookingRepository, never()).updateProviderStatus(anyLong(), eq("BUSY"));
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
    void testAddServicesToBooking_EmptyRequestList_Throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Services list must not be empty", ex.getReason());
        verify(bookingRepository, never()).findById(anyLong());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_NullRequestList_Throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Services list must not be empty", ex.getReason());
        verify(bookingRepository, never()).findById(anyLong());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_InitialBookingHasNoServices_Success() {
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

    @Test
    void testAddServicesToBooking_NullServiceItem_Throws400() {
        booking.setStatus(Booking.Status.REQUESTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        List<com.team28.booking.booking.dto.AddServiceItemDTO> services = new java.util.ArrayList<>();
        services.add(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, services));
        
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Service item must not be null", ex.getReason());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_NonExistentBooking_Throws404() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        com.team28.booking.booking.dto.AddServiceItemDTO s = new com.team28.booking.booking.dto.AddServiceItemDTO("S", 10, BigDecimal.TEN, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(99L, List.of(s)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_InProgressStatus_Throws400() {
        booking.setStatus(Booking.Status.IN_PROGRESS);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        com.team28.booking.booking.dto.AddServiceItemDTO s = new com.team28.booking.booking.dto.AddServiceItemDTO("S", 10, BigDecimal.TEN, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of(s)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Services can only be added when booking is REQUESTED or CONFIRMED", ex.getReason());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testAddServicesToBooking_CancelledStatus_Throws400() {
        booking.setStatus(Booking.Status.CANCELLED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        com.team28.booking.booking.dto.AddServiceItemDTO s = new com.team28.booking.booking.dto.AddServiceItemDTO("S", 10, BigDecimal.TEN, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.addServicesToBooking(1L, List.of(s)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Services can only be added when booking is REQUESTED or CONFIRMED", ex.getReason());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getBookingDetails_notFound_throws404() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getBookingDetails(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(bookingRepository).findById(1L);
    }

    @Test
    void getBookingDetails_success_3Services_mapsCorrectly() {
        Booking bookingWithServices = new Booking();
        bookingWithServices.setId(10L);
        bookingWithServices.setStatus(Booking.Status.IN_PROGRESS);
        bookingWithServices.setTotalPrice(BigDecimal.valueOf(100));

        BookingItem item1 = new BookingItem();
        item1.setId(101L);
        item1.setServiceOrder(2);
        item1.setServiceName("Service B");
        item1.setStatus(BookingItem.Status.COMPLETED);

        BookingItem item2 = new BookingItem();
        item2.setId(102L);
        item2.setServiceOrder(1);
        item2.setServiceName("Service A");
        item2.setStatus(BookingItem.Status.COMPLETED);

        BookingItem item3 = new BookingItem();
        item3.setId(103L);
        item3.setServiceOrder(3);
        item3.setServiceName("Service C");
        item3.setStatus(BookingItem.Status.PENDING);

        bookingWithServices.setBookingServices(List.of(item3, item1, item2));

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(bookingWithServices));

        BookingDetailsDTO result = bookingService.getBookingDetails(10L);

        assertEquals(10L, result.bookingId());
        assertEquals(Booking.Status.IN_PROGRESS, result.status());
        assertEquals(3, result.totalServices());
        assertEquals(2, result.completedServices());
        assertEquals(3, result.services().size());

        // Validate order
        assertEquals(102L, result.services().get(0).id()); // ServiceOrder 1
        assertEquals("Service A", result.services().get(0).serviceName());
        assertEquals(101L, result.services().get(1).id()); // ServiceOrder 2
        assertEquals("Service B", result.services().get(1).serviceName());
        assertEquals(103L, result.services().get(2).id()); // ServiceOrder 3
        assertEquals("Service C", result.services().get(2).serviceName());
    }

    @Test
    void getBookingDetails_noServices_mapsCorrectly() {
        Booking bookingNoServices = new Booking();
        bookingNoServices.setId(20L);
        bookingNoServices.setStatus(Booking.Status.CONFIRMED);
        bookingNoServices.setTotalPrice(BigDecimal.valueOf(50));
        bookingNoServices.setBookingServices(List.of());

        when(bookingRepository.findById(20L)).thenReturn(Optional.of(bookingNoServices));

        BookingDetailsDTO result = bookingService.getBookingDetails(20L);

        assertEquals(20L, result.bookingId());
        assertEquals(0, result.totalServices());
        assertEquals(0, result.completedServices());
        assertTrue(result.services().isEmpty());
    }
}
