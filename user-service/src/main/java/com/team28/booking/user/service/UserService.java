package com.team28.booking.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team28.booking.user.dto.SavedAddressDTO;
import com.team28.booking.user.dto.TopClientDTO;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.dto.UserProfileDTO;
import com.team28.booking.user.model.SavedAddress;
import com.team28.booking.user.model.Status;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.SavedAddressRepository;
import com.team28.booking.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public User save(User user) {
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User updateUser(Long userId, User updatedUser) {
        User existingUser = userRepository.findById(userId).orElse(null);
        if (existingUser == null) {
            throw new RuntimeException("User not found");
        }

        existingUser.setName(updatedUser.getName());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setPassword(updatedUser.getPassword());
        existingUser.setPhone(updatedUser.getPhone());
        existingUser.setRole(updatedUser.getRole());

        if (updatedUser.getStatus() != null) {
            existingUser.setStatus(updatedUser.getStatus());
        }

        existingUser.setPreferences(updatedUser.getPreferences());

        return userRepository.save(existingUser);
    }

    public void deleteUser(Long userId) {
        User existingUser = userRepository.findById(userId).orElse(null);
        if (existingUser == null) {
            throw new RuntimeException("User not found");
        }

        userRepository.delete(existingUser);
    }

    public SavedAddress createSavedAddress(Long userId, SavedAddress savedAddress) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        savedAddress.setId(null);
        savedAddress.setUser(user);
        return savedAddressRepository.save(savedAddress);
    }

    public List<SavedAddress> getAllSavedAddresses() {
        return savedAddressRepository.findAll();
    }

    public SavedAddress getSavedAddressById(Long addressId) {
        SavedAddress savedAddress = savedAddressRepository.findById(addressId).orElse(null);
        if (savedAddress == null) {
            throw new RuntimeException("Address not found");
        }
        return savedAddress;
    }

    public SavedAddress updateSavedAddress(Long addressId, SavedAddress updatedAddress) {
        SavedAddress existingAddress = savedAddressRepository.findById(addressId).orElse(null);
        if (existingAddress == null) {
            throw new RuntimeException("Address not found");
        }

        existingAddress.setLabel(updatedAddress.getLabel());
        existingAddress.setAddress(updatedAddress.getAddress());
        existingAddress.setLatitude(updatedAddress.getLatitude());
        existingAddress.setLongitude(updatedAddress.getLongitude());
        if (updatedAddress.getIsDefault() != null) {
            existingAddress.setIsDefault(updatedAddress.getIsDefault());
        }
        existingAddress.setMetadata(updatedAddress.getMetadata());

        if (updatedAddress.getUser() != null && updatedAddress.getUser().getId() != null) {
            User user = userRepository.findById(updatedAddress.getUser().getId()).orElse(null);
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            existingAddress.setUser(user);
        }

        return savedAddressRepository.save(existingAddress);
    }

    public void deleteSavedAddress(Long addressId) {
        SavedAddress existingAddress = savedAddressRepository.findById(addressId).orElse(null);
        if (existingAddress == null) {
            throw new RuntimeException("Address not found");
        }

        savedAddressRepository.delete(existingAddress);
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

        return new UserProfileDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getPreferences(),
                addressDTOs,
                (long) addressDTOs.size()
        );
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

        List<SavedAddress> userAddresses = savedAddressRepository.findByUserId(userId);
        SavedAddress targetAddress = null;
        for (SavedAddress address : userAddresses) {
            address.setIsDefault(false);
            if (addressId.equals(address.getId())) {
                targetAddress = address;
            }
        }

        if (targetAddress == null) {
            throw new IllegalArgumentException("Address does not belong to this user");
        }

        targetAddress.setIsDefault(true);
        savedAddressRepository.saveAll(userAddresses);
        return userRepository.findByIdWithSavedAddresses(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserBookingSummaryDTO getUserBookingSummary(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Object[] summaryRow = normalizeSummaryRow(userRepository.findUserBookingSummary(userId));
        if (summaryRow == null) {
            return new UserBookingSummaryDTO(
                    user.getId(),
                    user.getName(),
                    0L,
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        return new UserBookingSummaryDTO(
                ((Number) summaryRow[0]).longValue(),
                (String) summaryRow[1],
                ((Number) summaryRow[2]).longValue(),
                ((Number) summaryRow[3]).longValue(),
                ((Number) summaryRow[4]).longValue(),
                toBigDecimal(summaryRow[5]),
                toBigDecimal(summaryRow[6])
        );
    }

    private Object[] normalizeSummaryRow(Object summaryData) {
        if (summaryData == null) {
            return null;
        }

        if (summaryData instanceof List<?> rows) {
            if (rows.isEmpty()) {
                return null;
            }
            return normalizeSummaryRow(rows.get(0));
        }

        if (summaryData instanceof Object[] row) {
            if (row.length == 0) {
                return null;
            }

            if (row.length == 1 && (row[0] instanceof Object[] || row[0] instanceof List<?>)) {
                return normalizeSummaryRow(row[0]);
            }

            return row;
        }

        throw new IllegalStateException("Unexpected booking summary result type: " + summaryData.getClass().getName());
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
            TopClientDTO dto = new TopClientDTO();
            dto.setUserId(((Number) row[0]).longValue());
            dto.setName((String) row[1]);
            dto.setTotalSpent(((Number) row[2]).doubleValue());
            dto.setBookingCount(((Number) row[3]).longValue());
            topClients.add(dto);
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
