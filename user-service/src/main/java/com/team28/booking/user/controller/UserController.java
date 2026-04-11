package com.team28.booking.user.controller;

import com.team28.booking.user.dto.TopClientDTO;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.dto.UserProfileDTO;
import com.team28.booking.user.model.SavedAddress;
import com.team28.booking.user.model.User;
import com.team28.booking.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController              // ← REQUIRED: Marks this as REST controller
@RequestMapping("/api/users") // ← REQUIRED: Base path for all endpoints
public class UserController {

    @Autowired
    private UserService userService;

  // Health endpoint (PDF Section 4.1.9)
//    @GetMapping("/health")
//    public ResponseEntity<String> health() {
//        return ResponseEntity.ok("OK");
//    }

    // CRUD: Create User
    @PostMapping              // ← Maps to POST /api/users
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User saved = userService.save(user);
        return ResponseEntity.ok(saved);
    }

    // CRUD: Get All Users
    @GetMapping               // ← Maps to GET /api/users
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    // CRUD: Get User by ID
    @GetMapping("/{id}")      // ← Maps to GET /api/users/{id}
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    // CRUD: Update User
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            return ResponseEntity.ok(userService.updateUser(id, user));
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Delete User
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Create Saved Address
    @PostMapping("/{userId}/addresses")
    public ResponseEntity<SavedAddress> createSavedAddress(
            @PathVariable Long userId,
            @RequestBody SavedAddress savedAddress) {
        try {
            return ResponseEntity.ok(userService.createSavedAddress(userId, savedAddress));
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Get All Saved Addresses
    @GetMapping("/addresses")
    public ResponseEntity<List<SavedAddress>> getAllSavedAddresses() {
        return ResponseEntity.ok(userService.getAllSavedAddresses());
    }

    // CRUD: Get Saved Addresses for User
    @GetMapping("/{userId}/addresses")
    public ResponseEntity<List<SavedAddress>> getSavedAddressesByUserId(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(userService.getSavedAddressesByUserId(userId));
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Get Saved Address by ID
    @GetMapping("/addresses/{addressId}")
    public ResponseEntity<SavedAddress> getSavedAddressById(@PathVariable Long addressId) {
        try {
            return ResponseEntity.ok(userService.getSavedAddressById(addressId));
        } catch (RuntimeException e) {
            if ("Address not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Update Saved Address
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<SavedAddress> updateSavedAddress(
            @PathVariable Long addressId,
            @RequestBody SavedAddress savedAddress) {
        try {
            return ResponseEntity.ok(userService.updateSavedAddress(addressId, savedAddress));
        } catch (RuntimeException e) {
            if ("Address not found".equals(e.getMessage()) || "User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Delete Saved Address
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteSavedAddress(@PathVariable Long addressId) {
        try {
            userService.deleteSavedAddress(addressId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            if ("Address not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // S1-F2: Put User Preferences
    @PutMapping("/{id}/preferences")
    public ResponseEntity<User> updatePreferences(@PathVariable long id,
            @RequestBody Map<String, Object> updatedPreferences) {
        try {
            User updatedUser = userService.updateUserPreferences(id, updatedPreferences);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // S1-F3: Get User Booking Summary
    @GetMapping("/{id}/booking-summary")
    public ResponseEntity<UserBookingSummaryDTO> getUserBookingSummary(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.getUserBookingSummary(id));
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            throw e;
        }
    }

    @GetMapping("/preferences/search")
    public ResponseEntity<List<User>> getUsersByPreference(@RequestParam("key") String key,
            @RequestParam("value") String value) {
        try {
            return ResponseEntity.ok(userService.getUsersByPreference(key, value));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // S1-F7: Set Default Saved Address
    @PutMapping("/{userId}/addresses/{addressId}/default")
    public ResponseEntity<User> setDefaultSavedAddress(
            @PathVariable Long userId,
            @PathVariable Long addressId) {
        try {
            return ResponseEntity.ok(userService.setDefaultSavedAddress(userId, addressId));
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage()) || "Address not found".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
            if ("Address does not belong to this user".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
            throw e;
        }
    }

    // S1-F8: Get User Profile with Addresses
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.getUserProfile(id));
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            throw e;
        }
    }

    // S1-F9: Find Users by Language Preference with Minimum Bookings
    @GetMapping("/preferences/language")
    public ResponseEntity<List<User>> getUsersByLanguagePreference(
            @RequestParam String lang,
            @RequestParam long minBookings) {
        try {
            return ResponseEntity.ok(
                    userService.findUsersByLanguagePreferenceWithMinimumBookings(lang, minBookings));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // S1-F1: Search Users with Filters
    @GetMapping("/search")    // ← Maps to GET /api/users/search
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {

        List<User> users = userService.searchUsers(name, email, role);
        return ResponseEntity.ok(users);
    }

    // S1-F4: Deactivate User Account
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        try {
            User deactivatedUser = userService.deactivateUser(id);
            return ResponseEntity.ok(deactivatedUser);
        } catch (RuntimeException e) {
            if (e.getMessage().equals("User not found")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // S1-F6: Top Clients by Spending Report
    @GetMapping("/reports/top-clients")
    public ResponseEntity<List<TopClientDTO>> getTopClients(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            List<TopClientDTO> topClients = userService.getTopClientsBySpending(
                    startDate, endDate, limit);
            return ResponseEntity.ok(topClients);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

}
