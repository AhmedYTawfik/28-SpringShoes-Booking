package com.team28.booking.user.controller;

import com.team28.booking.user.dto.TopClientDTO;
import com.team28.booking.user.dto.UpdateRoleRequest;
import com.team28.booking.user.dto.UserActivityFeedDTO;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.dto.UserProfileDTO;
import com.team28.booking.user.model.User;
import com.team28.booking.user.model.User.Role;
import com.team28.booking.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
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
        user.setRole(User.Role.CLIENT);
        User saved = userService.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // CRUD: Get All Users
    @GetMapping               // ← Maps to GET /api/users
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    // CRUD: Get User by ID — ownership check (TC17: IDOR read protection)
    @GetMapping("/{id}")      // ← Maps to GET /api/users/{id}
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        // Ownership check: only owner or ADMIN may read
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    // CRUD: Update User — ownership check (TC20: owner update, TC22: admin update)
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        // Ownership check: only owner or ADMIN may update
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            User result = userService.updateUser(id, updatedUser);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CC-2: Change user role (ADMIN-only — gated by SecurityConfig + RoleAuthorizationHandler).
    // Token-staleness accepted limitation: the promoted/demoted user's existing JWT keeps its
    // old role claim until the 24h expiry. No token-revocation list is introduced (§9.2).
    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable Long id,
                                           @RequestBody UpdateRoleRequest request) {
        if (request.getRole() == null || request.getRole().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        User.Role newRole;
        try {
            newRole = User.Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            User updated = userService.changeRole(id, newRole);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        }
    }

    // CRUD: Delete User — ownership check (TC19: IDOR delete protection)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        // Ownership check: only owner or ADMIN may delete
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            if ("User not found".equals(e.getMessage())) {
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

    // S1-F12: paginated list of activity events
    @GetMapping("/{id}/activity")
    public ResponseEntity<UserActivityFeedDTO> getUserActivityFeed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = currentUser.getId();
        Role userRole = currentUser.getRole(); // change to accomodate multiple roles!

        if (!currentUserId.equals(id) && userRole != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Caller is neither the target user nor the an Admin");
        }
        try {
            if (size < 1 || page < 0) {
                return ResponseEntity.badRequest().build();
            }
            UserActivityFeedDTO userActivityFeedDTO = userService.getUserActivityFeed(id, page, size);
            return ResponseEntity.ok(userActivityFeedDTO);
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
