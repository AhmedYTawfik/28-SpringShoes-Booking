package com.team28.booking.user.controller;

import com.team28.booking.user.auth.JwtAuthenticationFilter;
import com.team28.booking.user.dto.AuthResponse;
import com.team28.booking.user.dto.RegisterRequest;
import com.team28.booking.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerRegisterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

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
    void register_validData_returns201() throws Exception {
        AuthResponse response = new AuthResponse("testToken", 86400000L);
        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Sara Kamal",
                                    "email": "sara@example.com",
                                    "password": "securePassword123",
                                    "phone": "+201012345678"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("testToken"))
                .andExpect(jsonPath("$.expiresIn").value(86400000));
    }

    @Test
    void register_blankField_returns400() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Blank fields"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "email": "sara@example.com",
                                    "password": "securePassword123",
                                    "phone": "+201012345678"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicate_returns409() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Sara Kamal",
                                    "email": "sara@example.com",
                                    "password": "securePassword123",
                                    "phone": "+201012345678"
                                }
                                """))
                .andExpect(status().isConflict());
    }
}
