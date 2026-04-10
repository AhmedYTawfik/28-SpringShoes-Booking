package com.team28.booking.user.service;

import com.team28.booking.user.model.User;
import com.team28.booking.user.model.User.Status;
import com.team28.booking.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User save(User user) {
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    // S1-F1: Search Users
    public List<User> searchUsers(String name, String email, String role) {
        String searchName = (name == null || name.trim().isEmpty()) ? null : name;
        String searchEmail = (email == null || email.trim().isEmpty()) ? null : email;
        String searchRole = (role == null || role.trim().isEmpty()) ? null : role;

        return userRepository.searchUsers(searchName, searchEmail, searchRole);
    }

    // S1-F4: Deactivate User Account (Transactional)
    @Transactional
    public User deactivateUser(Long userId) {
        // 1. Find user - throw 404 if not found
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found"); // Will be caught and converted to 404
        }

        // 2. Check no active bookings exist - throw 400 if active bookings found
        Long activeBookings = userRepository.countActiveBookings(userId);
        if (activeBookings != null && activeBookings > 0) {
            throw new IllegalStateException("User has active bookings");
        }

        // 3. Set status to DEACTIVATED
        user.setStatus(Status.DEACTIVATED);

        // 4. Save and return updated user
        return userRepository.save(user);
    }

}