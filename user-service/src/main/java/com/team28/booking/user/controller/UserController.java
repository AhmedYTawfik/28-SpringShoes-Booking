package com.team28.booking.user.controller;

import com.team28.booking.user.model.User;
import com.team28.booking.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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

}