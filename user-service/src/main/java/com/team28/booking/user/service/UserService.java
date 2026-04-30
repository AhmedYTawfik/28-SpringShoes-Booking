package com.team28.booking.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team28.booking.user.dto.SavedAddressDTO;
import com.team28.booking.user.dto.TopClientDTO;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.dto.UserProfileDTO;
import com.team28.booking.user.model.SavedAddress;
import com.team28.booking.user.model.User;
import com.team28.booking.user.model.User.Status;
import com.team28.booking.user.repository.SavedAddressRepository;
import com.team28.booking.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedAddressRepository savedAddressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public User save(User user) {
        String pw = user.getPassword();
        if (pw != null && !pw.startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(pw));
        }
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findByIdWithSavedAddresses(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        List<SavedAddressDTO> addressDTOs = new ArrayList<>();
        for (SavedAddress savedAddress : user.getSavedAddresses()) {
            addressDTOs.add(new SavedAddressDTO(
                    savedAddress.getLabel(),
                    savedAddress.getAddress(),
                    savedAddress.getLatitude(),
                    savedAddress.getLongitude(),
                    savedAddress.getIsDefault(),
                    savedAddress.getMetadata()
            ));
        }

        return UserProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .preferences(user.getPreferences())
                .savedAddresses(addressDTOs)
                .totalAddresses((long) addressDTOs.size())
                .build();
    }

    public List<User> findUsersByLanguagePreferenceWithMinimumBookings(String language, long minBookings) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language must not be blank");
        }

        return userRepository.findUsersByLanguagePreferenceAndMinimumCompletedBookings(
                language.trim(),
                minBookings
        );
    }

    // S1-F7: Set one saved address as default for the user.
    @Transactional
    public User setDefaultSavedAddress(Long userId, Long addressId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        SavedAddress requestedAddress = savedAddressRepository.findById(addressId).orElse(null);
        if (requestedAddress == null) {
            throw new RuntimeException("Address not found");
        }

        if (requestedAddress.getUser() == null || !userId.equals(requestedAddress.getUser().getId())) {
            throw new IllegalArgumentException("Address does not belong to this user");
        }

        SavedAddress targetAddress = null;
        for (SavedAddress address : user.getSavedAddresses()) {
            address.setIsDefault(false);
            if (addressId.equals(address.getId())) {
                targetAddress = address;
            }
        }

        if (targetAddress == null) {
            throw new IllegalArgumentException("Address does not belong to this user");
        }

        targetAddress.setIsDefault(true);
        return userRepository.save(user);
    }

    public UserBookingSummaryDTO getUserBookingSummary(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Object[] summaryRow = userRepository.findUserBookingSummary(userId);
        if (summaryRow == null) {
            return UserBookingSummaryDTO.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .totalBookings(0L)
                    .completedBookings(0L)
                    .cancelledBookings(0L)
                    .totalSpent(BigDecimal.ZERO)
                    .averageBookingPrice(BigDecimal.ZERO)
                    .build();
        }

        return UserBookingSummaryDTO.builder()
                .userId(((Number) summaryRow[0]).longValue())
                .name((String) summaryRow[1])
                .totalBookings(((Number) summaryRow[2]).longValue())
                .completedBookings(((Number) summaryRow[3]).longValue())
                .cancelledBookings(((Number) summaryRow[4]).longValue())
                .totalSpent(toBigDecimal(summaryRow[5]))
                .averageBookingPrice(toBigDecimal(summaryRow[6]))
                .build();
    }

    // S1-F1: Search Users
    public List<User> searchUsers(String name, String email, String role) {
        String searchName = (name == null || name.trim().isEmpty()) ? null : name;
        String searchEmail = (email == null || email.trim().isEmpty()) ? null : email;
        String searchRole = (role == null || role.trim().isEmpty()) ? null : role;

        return userRepository.searchUsers(searchName, searchEmail, searchRole);
    }

    public User updateUserPreferences(Long UserId, Map<String, Object> updatedPreferences) {
        User user = userRepository.findById(UserId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found"); // Will be caught and converted to 404
        }

        Map<String, Object> userPreferences = user.getPreferences();
        for (String key : updatedPreferences.keySet()) {
            userPreferences.put(key, updatedPreferences.get(key));
        }

        userRepository.save(user);
        return user;
    }

    public List<User> getUsersByPreference(String key, String value) {
        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Preference key and value must not be blank");
        }

        Map<String, Object> filter = Map.of(key.trim(), value.trim());

        try {
            String jsonFilter = objectMapper.writeValueAsString(filter);
            return userRepository.findByPreference(jsonFilter);
        } catch (JsonProcessingException e) {
            log.warn("failed to convert preference {} into json: {}", filter.toString(), e.getMessage());
            throw new IllegalStateException("Failed to build preference filter", e);
        }
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

    // S1-F6: Top Clients by Spending Report
    public List<TopClientDTO> getTopClientsBySpending(String startDate, String endDate, int limit) {
        // Validate dates
        validateDateRange(startDate, endDate);

        // Format dates for SQL (add time component)
        String startDateTime = startDate + " 00:00:00";
        String endDateTime = endDate + " 23:59:59";

        // Execute native query
        List<Object[]> results = userRepository.findTopClientsBySpending(
                startDateTime, endDateTime, limit);

        // Map Object[] results to DTOs
        List<TopClientDTO> topClients = new ArrayList<>();
        for (Object[] row : results) {
            topClients.add(TopClientDTO.builder()
                    .userId(((Number) row[0]).longValue())
                    .name((String) row[1])
                    .totalSpent(((Number) row[2]).doubleValue())
                    .bookingCount(((Number) row[3]).longValue())
                    .build());
        }

        return topClients;
    }

    private void validateDateRange(String startDate, String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            LocalDate start = LocalDate.parse(startDate, formatter);
            LocalDate end = LocalDate.parse(endDate, formatter);

            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Start date must be before or equal to end date");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format. Use yyyy-MM-dd");
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        return new BigDecimal(value.toString());
    }

}
