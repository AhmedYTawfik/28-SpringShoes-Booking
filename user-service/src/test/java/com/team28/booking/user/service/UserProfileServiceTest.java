package com.team28.booking.user.service;

import com.team28.booking.user.dto.UserProfileDTO;
import com.team28.booking.user.model.SavedAddress;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileServiceTest {

    @Test
    void getUserProfileReturnsAllUserAndAddressFields() {
        UserService userService = new UserService();
        User user = new User();
        user.setId(1L);
        user.setName("Ahmed");
        user.setEmail("ahmed@example.com");
        user.setPhone("0123456789");
        user.setSavedAddresses(new ArrayList<>());

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("language", "en");
        preferences.put("appointmentReminder", 30);
        user.setPreferences(preferences);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("landmark", "Next to Zamalek Club");

        SavedAddress home = new SavedAddress();
        home.setId(10L);
        home.setLabel("Home");
        home.setAddress("15 Nile St, Zamalek");
        home.setLatitude(30.056);
        home.setLongitude(31.219);
        home.setIsDefault(true);
        home.setMetadata(metadata);
        home.setUser(user);

        SavedAddress office = new SavedAddress();
        office.setId(11L);
        office.setLabel("Office");
        office.setAddress("42 Smart Village, Giza");
        office.setLatitude(30.071);
        office.setLongitude(31.017);
        office.setIsDefault(false);
        office.setMetadata(Map.of("buildingName", "Building B"));
        office.setUser(user);

        user.getSavedAddresses().add(home);
        user.getSavedAddresses().add(office);

        ReflectionTestUtils.setField(userService, "userRepository", stubUserRepository(Optional.of(user)));

        UserProfileDTO profile = userService.getUserProfile(1L);

        assertEquals(1L, profile.userId());
        assertEquals("Ahmed", profile.name());
        assertEquals("ahmed@example.com", profile.email());
        assertEquals("0123456789", profile.phone());
        assertEquals(preferences, profile.preferences());
        assertEquals(2L, profile.totalAddresses());
        assertEquals(2, profile.savedAddresses().size());
        assertEquals("Home", profile.savedAddresses().get(0).label());
        assertEquals("15 Nile St, Zamalek", profile.savedAddresses().get(0).address());
        assertEquals(30.056, profile.savedAddresses().get(0).lat());
        assertEquals(31.219, profile.savedAddresses().get(0).lng());
        assertTrue(profile.savedAddresses().get(0).isDefault());
        assertEquals(metadata, profile.savedAddresses().get(0).metadata());
    }

    @Test
    void getUserProfileReturnsEmptyAddressListWhenUserHasNoAddresses() {
        UserService userService = new UserService();
        User user = new User();
        user.setId(2L);
        user.setName("Sara");
        user.setEmail("sara@example.com");
        user.setPhone("0111111111");
        user.setPreferences(Map.of("language", "ar"));
        user.setSavedAddresses(new ArrayList<>());

        ReflectionTestUtils.setField(userService, "userRepository", stubUserRepository(Optional.of(user)));

        UserProfileDTO profile = userService.getUserProfile(2L);

        assertEquals(0L, profile.totalAddresses());
        assertTrue(profile.savedAddresses().isEmpty());
    }

    @Test
    void getUserProfileThrowsWhenUserDoesNotExist() {
        UserService userService = new UserService();
        ReflectionTestUtils.setField(userService, "userRepository", stubUserRepository(Optional.empty()));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.getUserProfile(999L)
        );

        assertEquals("User not found", exception.getMessage());
    }

    private UserRepository stubUserRepository(Optional<User> user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdWithSavedAddresses" -> user;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
