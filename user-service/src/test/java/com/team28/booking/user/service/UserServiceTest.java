package com.team28.booking.user.service;

import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
class UserServiceTest {

    @Test
    void updateUserReturnsUpdatedEntity() {
        UserService userService = new UserService();

        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setName("Ahmed");
        existingUser.setEmail("ahmed@example.com");
        existingUser.setPassword("old-pass");
        existingUser.setPhone("0100");
        existingUser.setRole(User.Role.CLIENT);
        existingUser.setStatus(User.Status.ACTIVE);

        User request = new User();
        request.setName("Ahmed Ali");
        request.setEmail("ahmed.ali@example.com");
        request.setPassword("new-pass");
        request.setPhone("0101");
        request.setRole(User.Role.ADMIN);
        request.setStatus(User.Status.DEACTIVATED);
        request.setPreferences(Map.of("language", "ar"));

        UserRepository userRepository = stubUserRepositoryForUpdate(existingUser);
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

        User updated = userService.updateUser(1L, request);

        assertEquals(1L, updated.getId());
        assertEquals("Ahmed Ali", updated.getName());
        assertEquals("ahmed.ali@example.com", updated.getEmail());
        assertEquals("new-pass", updated.getPassword());
        assertEquals("0101", updated.getPhone());
        assertEquals(User.Role.ADMIN, updated.getRole());
        assertEquals(User.Status.DEACTIVATED, updated.getStatus());
        assertEquals("ar", updated.getPreferences().get("language"));
    }

    @Test
    void updateUserThrowsWhenUserDoesNotExist() {
        UserService userService = new UserService();
        ReflectionTestUtils.setField(
                userService,
                "userRepository",
                stubUserRepositoryForUpdate(null)
        );

        User request = new User();

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.updateUser(999L, request)
        );

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void deleteUserDeletesExistingUser() {
        UserService userService = new UserService();
        User existingUser = new User();
        existingUser.setId(1L);

        ReflectionTestUtils.setField(
                userService,
                "userRepository",
                stubUserRepositoryForDelete(existingUser)
        );

        userService.deleteUser(1L);
    }

    @Test
    void deleteUserThrowsWhenUserDoesNotExist() {
        UserService userService = new UserService();
        ReflectionTestUtils.setField(
                userService,
                "userRepository",
                stubUserRepositoryForDelete(null)
        );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.deleteUser(999L)
        );

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void getUserBookingSummaryReturnsAggregatedValues() {
        UserService userService = new UserService();
        User user = new User();
        user.setId(1L);
        user.setName("Ahmed");

        Object[] summaryRow = new Object[]{
                1L,
                "Ahmed",
                5L,
                3L,
                1L,
                new BigDecimal("1000.00"),
                new BigDecimal("333.33")
        };

        UserRepository userRepository = stubUserRepository(Optional.of(user), summaryRow);
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

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

        Object[] summaryRow = new Object[]{
                2L,
                "Sara",
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        };

        UserRepository userRepository = stubUserRepository(Optional.of(user), summaryRow);
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

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
        UserRepository userRepository = stubUserRepository(Optional.empty(), null);
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.getUserBookingSummary(999L)
        );

        assertEquals("User not found", exception.getMessage());
    }

    private UserRepository stubUserRepository(Optional<User> user, Object[] summaryRow) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findById" -> user;
                        case "findUserBookingSummary" -> summaryRow;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }
        );
    }

    private UserRepository stubUserRepositoryForUpdate(User existingUser) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(existingUser);
                        case "save" -> args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }
        );
    }

    private UserRepository stubUserRepositoryForDelete(User existingUser) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(existingUser);
                        case "delete" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }
        );
    }
}
