package com.team28.booking.user.service;

import com.team28.booking.contracts.dto.BookingSummaryDTO;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.user.adapter.ObjectArrayDtoAdapter;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    @Test
    void getUserBookingSummaryReturnsAggregatedValues() {
        UserService userService = new UserService();
        User user = new User();
        user.setId(1L);
        user.setName("Ahmed");

        UserRepository userRepository = stubUserRepository(Optional.of(user));
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

        BookingServiceClient bookingServiceClient = stubBookingServiceClient(
                new BookingSummaryDTO(5L, 3L, 1L, new BigDecimal("1000.00"), new BigDecimal("333.33"))
        );
        ReflectionTestUtils.setField(userService, "bookingServiceClient", bookingServiceClient);

        UserBookingSummaryDTO summary = userService.getUserBookingSummary(1L);

        assertEquals(1L, summary.userId());
        assertEquals("Ahmed", summary.name());
        assertEquals(5L, summary.totalBookings());
        assertEquals(3L, summary.completedBookings());
        assertEquals(1L, summary.cancelledBookings());
        assertEquals(new BigDecimal("1000.00"), summary.totalSpent());
        assertEquals(new BigDecimal("333.33"), summary.averageBookingPrice());
    }

    @Test
    void getUserBookingSummaryReturnsZerosWhenUserHasNoBookings() {
        UserService userService = new UserService();
        User user = new User();
        user.setId(2L);
        user.setName("Sara");

        UserRepository userRepository = stubUserRepository(Optional.of(user));
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

        BookingServiceClient bookingServiceClient = stubBookingServiceClient(
                new BookingSummaryDTO(0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO)
        );
        ReflectionTestUtils.setField(userService, "bookingServiceClient", bookingServiceClient);

        UserBookingSummaryDTO summary = userService.getUserBookingSummary(2L);

        assertEquals(0L, summary.totalBookings());
        assertEquals(0L, summary.completedBookings());
        assertEquals(0L, summary.cancelledBookings());
        assertEquals(BigDecimal.ZERO, summary.totalSpent());
        assertEquals(BigDecimal.ZERO, summary.averageBookingPrice());
    }

    @Test
    void getUserBookingSummaryThrowsWhenUserDoesNotExist() {
        UserService userService = new UserService();
        UserRepository userRepository = stubUserRepository(Optional.empty());
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.getUserBookingSummary(999L)
        );

        assertEquals("User not found", exception.getMessage());
    }

    private UserRepository stubUserRepository(Optional<User> user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return user;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private BookingServiceClient stubBookingServiceClient(BookingSummaryDTO summary) {
        return (BookingServiceClient) Proxy.newProxyInstance(
                BookingServiceClient.class.getClassLoader(),
                new Class<?>[]{BookingServiceClient.class},
                (proxy, method, args) -> {
                    if ("getUserBookingSummary".equals(method.getName())) {
                        return summary;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
