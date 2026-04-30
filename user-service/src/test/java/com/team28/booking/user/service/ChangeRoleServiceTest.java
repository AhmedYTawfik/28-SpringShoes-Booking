package com.team28.booking.user.service;

import com.team28.booking.user.cache.CacheInvalidator;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [CC-2] Role management endpoint — unit tests for UserService.changeRole
 */
class ChangeRoleServiceTest {

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void changeRole_toAdmin_updatesAndReturns() {
        User user = new User();
        user.setId(1L);
        user.setRole(User.Role.CLIENT);

        UserService userService = buildService(Optional.of(user));

        User result = userService.changeRole(1L, User.Role.ADMIN);

        assertEquals(User.Role.ADMIN, result.getRole());
    }

    @Test
    void changeRole_toClient_updatesAndReturns() {
        User user = new User();
        user.setId(2L);
        user.setRole(User.Role.ADMIN);

        UserService userService = buildService(Optional.of(user));

        User result = userService.changeRole(2L, User.Role.CLIENT);

        assertEquals(User.Role.CLIENT, result.getRole());
    }

    // ── 404 ────────────────────────────────────────────────────────────────────

    @Test
    void changeRole_missingUser_throwsRuntimeException() {
        UserService userService = buildService(Optional.empty());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> userService.changeRole(99L, User.Role.ADMIN)
        );

        assertEquals("User not found", ex.getMessage());
    }

    // ── 400 — invalid role value is guarded at controller level; service receives Role enum ────

    @Test
    void changeRole_sameRole_idempotent() {
        User user = new User();
        user.setId(3L);
        user.setRole(User.Role.CLIENT);

        UserService userService = buildService(Optional.of(user));

        User result = userService.changeRole(3L, User.Role.CLIENT);

        assertEquals(User.Role.CLIENT, result.getRole());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserService buildService(Optional<User> userOpt) {
        UserService userService = new UserService();

        UserRepository userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> userOpt;
                    case "save" -> args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "cacheInvalidator", Mockito.mock(CacheInvalidator.class));
        return userService;
    }
}
