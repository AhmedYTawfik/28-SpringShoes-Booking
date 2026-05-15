package com.team28.booking.user.service;

import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserLanguagePreferenceServiceTest {

    @Test
    void findUsersByLanguagePreferenceWithMinimumBookingsReturnsMatchingUsers() {
        UserService userService = new UserService();
        User userA = buildUser(1L, "User A", "ar");
        User userB = buildUser(2L, "User B", "ar");
        List<User> expectedUsers = List.of(userA); // User B has 0 bookings in our stub, so it's filtered out

        ReflectionTestUtils.setField(
                userService,
                "userRepository",
                stubUserRepository(List.of(userA, userB))
        );
        ReflectionTestUtils.setField(
                userService,
                "bookingServiceClient",
                stubBookingServiceClient()
        );

        List<User> users = userService.findUsersByLanguagePreferenceWithMinimumBookings("ar", 1);

        assertIterableEquals(expectedUsers, users);
    }

    @Test
    void findUsersByLanguagePreferenceWithMinimumBookingsTrimsLanguage() {
        UserService userService = new UserService();
        User user = buildUser(1L, "User A", "ar");
        CapturedCall capturedCall = new CapturedCall();

        ReflectionTestUtils.setField(
                userService,
                "userRepository",
                stubUserRepository(List.of(user), capturedCall)
        );
        ReflectionTestUtils.setField(
                userService,
                "bookingServiceClient",
                stubBookingServiceClient()
        );

        userService.findUsersByLanguagePreferenceWithMinimumBookings("  ar  ", 1);

        assertEquals("ar", capturedCall.language);
    }

    @Test
    void findUsersByLanguagePreferenceWithMinimumBookingsThrowsWhenLanguageBlank() {
        UserService userService = new UserService();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.findUsersByLanguagePreferenceWithMinimumBookings("   ", 1)
        );

        assertEquals("Language must not be blank", exception.getMessage());
    }

    private User buildUser(Long id, String name, String language) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setPreferences(Map.of("language", language));
        return user;
    }

    private UserRepository stubUserRepository(List<User> users) {
        return stubUserRepository(users, new CapturedCall());
    }

    private UserRepository stubUserRepository(List<User> users, CapturedCall capturedCall) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findUsersByLanguagePreference".equals(method.getName())) {
                        capturedCall.language = (String) args[0];
                        return users;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private BookingServiceClient stubBookingServiceClient() {
        return (BookingServiceClient) Proxy.newProxyInstance(
                BookingServiceClient.class.getClassLoader(),
                new Class<?>[]{BookingServiceClient.class},
                (proxy, method, args) -> {
                    if ("getCompletedBookingCount".equals(method.getName())) {
                        Long userId = (Long) args[0];
                        if (userId.equals(1L)) {
                            return 3L; // User A has 3 bookings
                        } else {
                            return 0L; // User B has 0 bookings
                        }
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static class CapturedCall {
        private String language;
    }
}
