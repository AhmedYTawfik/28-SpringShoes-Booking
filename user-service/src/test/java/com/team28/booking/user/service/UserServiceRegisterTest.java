package com.team28.booking.user.service;

import com.team28.booking.user.auth.JwtService;
import com.team28.booking.user.cache.CacheInvalidator;
import com.team28.booking.user.dto.AuthResponse;
import com.team28.booking.user.dto.RegisterRequest;
import com.team28.booking.user.messaging.UserEventPublisher;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceRegisterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private CacheInvalidator cacheInvalidator;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest("Sara Kamal", "sara@example.com", "securePassword123", "+201012345678");
    }

    @Test
    void register_validData_returnsAuthResponse() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setEmail(validRequest.getEmail());
        savedUser.setRole(User.Role.CLIENT);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        when(jwtService.issue(anyString(), anyLong(), anyString())).thenReturn("testToken");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = userService.register(validRequest);

        assertNotNull(response);
        assertEquals("testToken", response.getToken());
        assertEquals(86400000L, response.getExpiresIn());
        
        verify(userRepository).save(argThat(user -> 
            user.getName().equals("Sara Kamal") &&
            user.getEmail().equals("sara@example.com") &&
            user.getPassword().equals("hashedPassword") &&
            user.getRole() == User.Role.CLIENT
        ));
    }

    @Test
    void register_blankName_throwsIllegalArgumentException() {
        validRequest.setName("");
        assertThrows(IllegalArgumentException.class, () -> userService.register(validRequest));
    }

    @Test
    void register_duplicateEmail_throwsRuntimeException() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(validRequest));
        assertTrue(ex.getMessage().contains("Email already registered"));
    }

    @Test
    void register_duplicatePhone_throwsRuntimeException() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone(validRequest.getPhone())).thenReturn(true);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(validRequest));
        assertTrue(ex.getMessage().contains("Phone already registered"));
    }
}
