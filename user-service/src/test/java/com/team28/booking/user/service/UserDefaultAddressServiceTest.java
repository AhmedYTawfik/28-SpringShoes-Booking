package com.team28.booking.user.service;

import com.team28.booking.user.cache.CacheInvalidator;
import com.team28.booking.user.model.SavedAddress;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.SavedAddressRepository;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserDefaultAddressServiceTest {

    @Test
    void setDefaultSavedAddressUpdatesOnlyRequestedAddress() {
        UserService userService = new UserService();
        User user = new User();
        user.setId(1L);
        user.setSavedAddresses(new ArrayList<>());

        SavedAddress addressOne = buildAddress(11L, user, false);
        SavedAddress addressTwo = buildAddress(12L, user, true);
        SavedAddress addressThree = buildAddress(13L, user, false);
        user.getSavedAddresses().add(addressOne);
        user.getSavedAddresses().add(addressTwo);
        user.getSavedAddresses().add(addressThree);

        UserRepository userRepository = stubUserRepository(Optional.of(user));
        SavedAddressRepository savedAddressRepository = stubSavedAddressRepository(Optional.of(addressThree));

        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "savedAddressRepository", savedAddressRepository);
        ReflectionTestUtils.setField(userService, "cacheInvalidator", Mockito.mock(CacheInvalidator.class));

        User updatedUser = userService.setDefaultSavedAddress(1L, 13L);

        assertSame(user, updatedUser);
        assertFalse(addressOne.getIsDefault());
        assertFalse(addressTwo.getIsDefault());
        assertTrue(addressThree.getIsDefault());
    }

    @Test
    void setDefaultSavedAddressThrowsWhenUserDoesNotExist() {
        UserService userService = new UserService();
        ReflectionTestUtils.setField(userService, "userRepository", stubUserRepository(Optional.empty()));
        ReflectionTestUtils.setField(userService, "savedAddressRepository",
                stubSavedAddressRepository(Optional.empty()));
        ReflectionTestUtils.setField(userService, "cacheInvalidator", Mockito.mock(CacheInvalidator.class));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.setDefaultSavedAddress(99L, 10L)
        );

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void setDefaultSavedAddressThrowsWhenAddressDoesNotBelongToUser() {
        UserService userService = new UserService();

        User owner = new User();
        owner.setId(1L);
        owner.setSavedAddresses(new ArrayList<>());

        User anotherUser = new User();
        anotherUser.setId(2L);

        SavedAddress foreignAddress = buildAddress(30L, anotherUser, true);
        owner.setSavedAddresses(List.of(buildAddress(10L, owner, false)));

        ReflectionTestUtils.setField(userService, "userRepository", stubUserRepository(Optional.of(owner)));
        ReflectionTestUtils.setField(userService, "savedAddressRepository",
                stubSavedAddressRepository(Optional.of(foreignAddress)));
        ReflectionTestUtils.setField(userService, "cacheInvalidator", Mockito.mock(CacheInvalidator.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.setDefaultSavedAddress(1L, 30L)
        );

        assertEquals("Address does not belong to this user", exception.getMessage());
    }

    private UserRepository stubUserRepository(Optional<User> user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> user;
                    case "save" -> args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private SavedAddressRepository stubSavedAddressRepository(Optional<SavedAddress> address) {
        return (SavedAddressRepository) Proxy.newProxyInstance(
                SavedAddressRepository.class.getClassLoader(),
                new Class<?>[]{SavedAddressRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> address;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private SavedAddress buildAddress(Long id, User user, boolean isDefault) {
        SavedAddress address = new SavedAddress();
        address.setId(id);
        address.setUser(user);
        address.setIsDefault(isDefault);
        return address;
    }
}
