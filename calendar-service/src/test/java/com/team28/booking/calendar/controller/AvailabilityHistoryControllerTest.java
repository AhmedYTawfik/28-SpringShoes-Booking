package com.team28.booking.calendar.controller;

import com.team28.booking.calendar.auth.JwtAuthenticationFilter;
import com.team28.booking.calendar.dto.AvailabilitySnapshotDTO;
import com.team28.booking.calendar.service.AvailabilityHistoryService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvailabilityHistoryController.class)
class AvailabilityHistoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AvailabilityHistoryService availabilityHistoryService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final Long PROVIDER_ID = 42L;
    private static final Instant T_1400 = Instant.parse("2026-05-02T14:00:00Z");
    private static final Instant T_1415 = Instant.parse("2026-05-02T14:15:00Z");
    private static final Instant T_1430 = Instant.parse("2026-05-02T14:30:00Z");

    @BeforeEach
    void bypassJwtFilter() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private AvailabilitySnapshotDTO snapshot(Instant ts, double utilization) {
        return new AvailabilitySnapshotDTO(PROVIDER_ID, ts, "2026-05-02", 10, 5, 5, utilization, "ok");
    }

    @Test
    void getHistory_returnsAllSnapshotsNewestFirst() throws Exception {
        when(availabilityHistoryService.getAvailabilityHistory(eq(PROVIDER_ID), isNull(), isNull()))
                .thenReturn(List.of(snapshot(T_1430, 0.9), snapshot(T_1415, 0.6), snapshot(T_1400, 0.3)));

        mockMvc.perform(get("/api/calendar/{id}/availability-history", PROVIDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].timestamp").value("2026-05-02T14:30:00Z"))
                .andExpect(jsonPath("$[2].timestamp").value("2026-05-02T14:00:00Z"));
    }

    @Test
    void getHistory_withRange_filtersSnapshots() throws Exception {
        Instant from = Instant.parse("2026-05-02T14:10:00Z");
        Instant to   = Instant.parse("2026-05-02T14:20:00Z");
        when(availabilityHistoryService.getAvailabilityHistory(eq(PROVIDER_ID), eq(from), eq(to)))
                .thenReturn(List.of(snapshot(T_1415, 0.6)));

        mockMvc.perform(get("/api/calendar/{id}/availability-history", PROVIDER_ID)
                        .param("startTime", "2026-05-02T14:10:00Z")
                        .param("endTime",   "2026-05-02T14:20:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].timestamp").value("2026-05-02T14:15:00Z"));
    }

    @Test
    void getHistory_providerNotFound_returns404() throws Exception {
        when(availabilityHistoryService.getAvailabilityHistory(eq(999L), isNull(), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));

        mockMvc.perform(get("/api/calendar/{id}/availability-history", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_providerWithNoSnapshots_returnsEmptyList() throws Exception {
        when(availabilityHistoryService.getAvailabilityHistory(eq(PROVIDER_ID), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/calendar/{id}/availability-history", PROVIDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHistory_withoutToken_returns401() throws Exception {
        doAnswer(inv -> {
            HttpServletResponse resp = inv.getArgument(1);
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        mockMvc.perform(get("/api/calendar/{id}/availability-history", PROVIDER_ID))
                .andExpect(status().isUnauthorized());
    }
}