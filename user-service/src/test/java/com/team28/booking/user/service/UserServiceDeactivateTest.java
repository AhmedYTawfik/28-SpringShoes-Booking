package com.team28.booking.user.service;

import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.user.cache.CacheInvalidator;
import com.team28.booking.user.exception.ServiceUnavailableException;
import com.team28.booking.user.messaging.UserEventPublisher;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeactivateTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingServiceClient bookingServiceClient;

    @Mock
    private CacheInvalidator cacheInvalidator;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setStatus(User.Status.ACTIVE);
    }

    @Test
    void deactivateUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getActiveBookingCount(1L)).thenReturn(0);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User deactivatedUser = userService.deactivateUser(1L);

        assertNotNull(deactivatedUser);
        assertEquals(User.Status.DEACTIVATED, deactivatedUser.getStatus());
        verify(userRepository).save(user);
        verify(cacheInvalidator, atLeastOnce()).deleteKey(anyString());
    }

    @Test
    void deactivateUser_userNotFound_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deactivateUser(1L));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void deactivateUser_alreadyDeactivated_throwsException() {
        user.setStatus(User.Status.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.deactivateUser(1L));
        assertEquals("User is already deactivated", ex.getMessage());
    }

    @Test
    void deactivateUser_hasActiveBookings_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getActiveBookingCount(1L)).thenReturn(2);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.deactivateUser(1L));
        assertEquals("User has active bookings", ex.getMessage());
    }

    @Test
    void deactivateUser_feignException_throwsServiceUnavailableException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getActiveBookingCount(1L)).thenThrow(mock(FeignException.class));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> userService.deactivateUser(1L));
        assertEquals("Booking service temporarily unavailable", ex.getMessage());
    }
}