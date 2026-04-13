package com.team28.booking.booking.controller;

import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.ServiceDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.service.BookingService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

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

    // --- GET /api/bookings/{id}/details ---

    @Test
    void getBookingDetails_validId_returnsBookingDetails() throws Exception {
        ServiceDetailsDTO service1 = new ServiceDetailsDTO(1L, 1, "Haircut", 60, BigDecimal.valueOf(50.0), BookingItem.Status.COMPLETED, Map.of());
        BookingDetailsDTO detailsDTO = new BookingDetailsDTO(
                100L, 10L, 5L, Booking.Status.CONFIRMED, BigDecimal.valueOf(50.0), Map.of("notes", "VIP"), List.of(service1), 1, 1);
                
        when(bookingService.getBookingDetails(100L)).thenReturn(detailsDTO);

        mockMvc.perform(get("/api/bookings/100/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(100))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.providerId").value(5))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalPrice").value(50.0))
                .andExpect(jsonPath("$.metadata.notes").value("VIP"))
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].id").value(1))
                .andExpect(jsonPath("$.services[0].serviceName").value("Haircut"))
                .andExpect(jsonPath("$.totalServices").value(1))
                .andExpect(jsonPath("$.completedServices").value(1));
    }

    @Test
    void getBookingDetails_notFoundId_returnsNotFound() throws Exception {
        when(bookingService.getBookingDetails(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: 999"));

        mockMvc.perform(get("/api/bookings/999/details"))
                .andExpect(status().isNotFound());
    }
}
