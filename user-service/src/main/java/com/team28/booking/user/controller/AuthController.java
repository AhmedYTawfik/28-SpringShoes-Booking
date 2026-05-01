package com.team28.booking.user.controller;

import com.team28.booking.user.dto.AuthResponse;
import com.team28.booking.user.dto.RegisterRequest;
import com.team28.booking.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            if (e.getMessage().contains("already registered")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            }
            throw e;
        }
    }
}
