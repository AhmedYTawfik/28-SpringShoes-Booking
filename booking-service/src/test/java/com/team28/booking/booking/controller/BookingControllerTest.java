package com.team28.booking.booking.controller;

import com.team28.booking.booking.auth.JwtAuthenticationFilter;
import com.team28.booking.booking.dto.BookingAnalyticsDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.ServiceDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.service.BookingService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void bypassJwtFilter() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void estimate_lowDemand_returnsMultiplierOne() throws Exception {
        when(bookingService.getEstimate(any())).thenReturn(
                new BookingEstimateDTO(90, BigDecimal.valueOf(450.0), BigDecimal.valueOf(450.0), BigDecimal.valueOf(1.0)));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"appointmentDate":"2026-04-15","services":[{"serviceName":"Haircut","duration":60},{"serviceName":"Wash","duration":30}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDuration").value(90))
                .andExpect(jsonPath("$.basePrice").value(450.0))
                .andExpect(jsonPath("$.demandMultiplier").value(1.0))
                .andExpect(jsonPath("$.estimatedPrice").value(450.0));
    }

    @Test
    void estimate_mediumDemand_returnsMultiplierOnePointTwoFive() throws Exception {
        when(bookingService.getEstimate(any())).thenReturn(
                new BookingEstimateDTO(90, BigDecimal.valueOf(450.0), BigDecimal.valueOf(562.5), BigDecimal.valueOf(1.25)));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"appointmentDate":"2026-04-15","services":[{"serviceName":"Haircut","duration":60},{"serviceName":"Wash","duration":30}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandMultiplier").value(1.25))
                .andExpect(jsonPath("$.estimatedPrice").value(562.5));
    }

    @Test
    void estimate_highDemand_returnsMultiplierOnePointFive() throws Exception {
        when(bookingService.getEstimate(any())).thenReturn(
                new BookingEstimateDTO(60, BigDecimal.valueOf(300.0), BigDecimal.valueOf(450.0), BigDecimal.valueOf(1.5)));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"appointmentDate":"2026-04-15","services":[{"serviceName":"Haircut","duration":60}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandMultiplier").value(1.5))
                .andExpect(jsonPath("$.estimatedPrice").value(450.0));
    }

    @Test
    void estimate_singleService_returnsCorrectDurationAndBasePrice() throws Exception {
        when(bookingService.getEstimate(any())).thenReturn(
                new BookingEstimateDTO(45, BigDecimal.valueOf(225.0), BigDecimal.valueOf(225.0), BigDecimal.valueOf(1.0)));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":2,"appointmentDate":"2026-05-01","services":[{"serviceName":"Manicure","duration":45}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDuration").value(45))
                .andExpect(jsonPath("$.basePrice").value(225.0));
    }

    @Test
    void estimate_emptyServices_returnsBadRequest() throws Exception {
        when(bookingService.getEstimate(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Services list must not be empty"));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"appointmentDate":"2026-04-15","services":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estimate_negativeDuration_returnsBadRequest() throws Exception {
        when(bookingService.getEstimate(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service duration must be positive"));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"appointmentDate":"2026-04-15","services":[{"serviceName":"X","duration":-5}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- S3-F5: GET /api/bookings/metadata/search ---

    @Test
    void metadataSearch_validParams_returnsMatchingBookings() throws Exception {
        Booking b1 = new Booking();
        b1.setId(1L);
        Booking b2 = new Booking();
        b2.setId(2L);
        when(bookingService.searchByMetadata("bookingType", "IN_PERSON")).thenReturn(List.of(b1, b2));

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "bookingType")
                        .param("value", "IN_PERSON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void metadataSearch_noMatches_returnsEmptyArray() throws Exception {
        when(bookingService.searchByMetadata("bookingType", "VIRTUAL")).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "bookingType")
                        .param("value", "VIRTUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void metadataSearch_blankKey_returnsBadRequest() throws Exception {
        when(bookingService.searchByMetadata(eq(""), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key must not be blank"));

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "")
                        .param("value", "IN_PERSON"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metadataSearch_blankValue_returnsBadRequest() throws Exception {
        when(bookingService.searchByMetadata(any(), eq("")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata value must not be blank"));

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "bookingType")
                        .param("value", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metadataSearch_returnsJsonContentType() throws Exception {
        when(bookingService.searchByMetadata(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "bookingType")
                        .param("value", "IN_PERSON"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void metadataSearch_passesCorrectParamsToService() throws Exception {
        when(bookingService.searchByMetadata("priorityLevel", "EXPRESS")).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "priorityLevel")
                        .param("value", "EXPRESS"))
                .andExpect(status().isOk());

        verify(bookingService).searchByMetadata("priorityLevel", "EXPRESS");
    }

    @Test
    void metadataSearch_missingKey_returns400() throws Exception {
        // Spring rejects the request before the controller method is reached when a required
        // @RequestParam is absent entirely — distinct from the blank-string case handled by the service.
        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("value", "IN_PERSON"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metadataSearch_missingValue_returns400() throws Exception {
        mockMvc.perform(get("/api/bookings/metadata/search")
                        .param("key", "bookingType"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estimate_returnsJsonContentType() throws Exception {
        when(bookingService.getEstimate(any())).thenReturn(
                new BookingEstimateDTO(60, BigDecimal.valueOf(300.0), BigDecimal.valueOf(300.0), BigDecimal.valueOf(1.0)));

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"appointmentDate":"2026-04-15","services":[{"serviceName":"X","duration":60}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // --- S3-F4: PUT /api/bookings/{id}/complete ---

    @Test
    void completeBooking_inProgress_returns200() throws Exception {
        Booking completed = new Booking();
        completed.setId(1L);
        completed.setStatus(Booking.Status.COMPLETED);
        when(bookingService.completeBooking(1L)).thenReturn(completed);

        mockMvc.perform(put("/api/bookings/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void completeBooking_wrongStatus_returns400() throws Exception {
        when(bookingService.completeBooking(1L))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Booking must be IN_PROGRESS to complete"));

        mockMvc.perform(put("/api/bookings/1/complete"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeBooking_notFound_returns404() throws Exception {
        when(bookingService.completeBooking(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        mockMvc.perform(put("/api/bookings/999/complete"))
                .andExpect(status().isNotFound());
    }

    // --- S3-F6: GET /api/bookings/analytics ---

    @Test
    void getAnalytics_validDates_returns200() throws Exception {
        BookingAnalyticsDTO dto = new BookingAnalyticsDTO(10L, 7L, 2L,
                new BigDecimal("1400.00"), new BigDecimal("200.00"), 70.0);
        when(bookingService.getAnalytics(any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/bookings/analytics")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(10))
                .andExpect(jsonPath("$.completedBookings").value(7))
                .andExpect(jsonPath("$.completionRate").value(70.0));
    }

    @Test
    void getAnalytics_startAfterEnd_returns400() throws Exception {
        when(bookingService.getAnalytics(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "startDate must be on or before endDate"));

        mockMvc.perform(get("/api/bookings/analytics")
                        .param("startDate", "2026-12-31")
                        .param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnalytics_missingStartDate_returns400() throws Exception {
        mockMvc.perform(get("/api/bookings/analytics")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnalytics_passesLocalDateParamsToService() throws Exception {
        BookingAnalyticsDTO dto = new BookingAnalyticsDTO(0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
        when(bookingService.getAnalytics(any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/bookings/analytics")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk());

        verify(bookingService).getAnalytics(
                eq(java.time.LocalDate.of(2026, 3, 1)),
                eq(java.time.LocalDate.of(2026, 3, 31)));
    }

    // --- S3-F9: GET /api/bookings/{id}/details ---

    @Test
    void getBookingDetails_exists_returns200() throws Exception {
        ServiceDetailsDTO svc = new ServiceDetailsDTO(1L, 1, "Haircut", 30,
                BigDecimal.valueOf(100), BookingItem.Status.COMPLETED, Map.of());
        BookingDetailsDTO dto = new BookingDetailsDTO(1L, 10L, 5L,
                Booking.Status.IN_PROGRESS, BigDecimal.valueOf(100), Map.of(),
                List.of(svc), 1, 1);
        when(bookingService.getBookingDetails(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/bookings/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(1))
                .andExpect(jsonPath("$.totalServices").value(1))
                .andExpect(jsonPath("$.completedServices").value(1))
                .andExpect(jsonPath("$.services[0].serviceName").value("Haircut"));
    }

    @Test
    void getBookingDetails_notFound_returns404() throws Exception {
        when(bookingService.getBookingDetails(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        mockMvc.perform(get("/api/bookings/999/details"))
                .andExpect(status().isNotFound());
    }

    // --- S3-F1: GET /api/bookings/search ---

    @Test
    void searchBookings_withStatus_returns200() throws Exception {
        Booking b = new Booking();
        b.setId(1L);
        b.setStatus(Booking.Status.CONFIRMED);
        when(bookingService.searchBookings(eq("CONFIRMED"), any(), any()))
                .thenReturn(List.of(b));

        mockMvc.perform(get("/api/bookings/search")
                        .param("status", "CONFIRMED")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void searchBookings_noStatus_passesNullToService() throws Exception {
        when(bookingService.searchBookings(isNull(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/search")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk());

        verify(bookingService).searchBookings(isNull(), any(), any());
    }

    @Test
    void searchBookings_missingStartDate_returns400() throws Exception {
        mockMvc.perform(get("/api/bookings/search")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isBadRequest());
    }
}
