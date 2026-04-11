package com.team28.booking.booking.controller;

import com.team28.booking.booking.dto.BookingEstimateDTO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
}
