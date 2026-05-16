package com.team28.booking.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team28.booking.user.adapter.ObjectArrayDtoAdapter;
import com.team28.booking.user.cache.CacheInvalidator;
import com.team28.booking.user.auth.JwtService;
import com.team28.booking.user.dto.AuthResponse;
import com.team28.booking.user.dto.LoginRequest;
import com.team28.booking.user.dto.RegisterRequest;
import com.team28.booking.user.dto.SavedAddressDTO;
import com.team28.booking.user.dto.TopClientDTO;
import com.team28.booking.user.dto.UserActivityFeedDTO;
import com.team28.booking.user.dto.UserBookingSummaryDTO;
import com.team28.booking.user.dto.UserProfileDTO;
import com.team28.booking.user.model.SavedAddress;
import com.team28.booking.user.model.User;
import com.team28.booking.user.model.User.Role;
import com.team28.booking.user.model.User.Status;
import com.team28.booking.user.mongo.AuthEvent;
import com.team28.booking.user.mongo.AuthEventRepository;
import com.team28.booking.user.observer.MongoEventLogger;
import com.team28.booking.user.observer.Observable;
import com.team28.booking.user.messaging.UserEventPublisher;
import com.team28.booking.user.repository.SavedAddressRepository;
import com.team28.booking.user.repository.UserRepository;
import com.team28.booking.user.exception.ServiceUnavailableException;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.feign.InvoiceServiceClient;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
import feign.FeignException;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService extends Observable {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingServiceClient bookingServiceClient;

    @Autowired
    private InvoiceServiceClient invoiceServiceClient;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private SavedAddressRepository savedAddressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectArrayDtoAdapter objectArrayDtoAdapter;

    @Autowired
    private MongoEventLogger mongoEventLogger;

    @Autowired
    private CacheInvalidator cacheInvalidator;

    @Autowired
    private UserEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @PostConstruct
    void registerObservers() {
        register(mongoEventLogger);
    }

    // ── writes ───────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getName() == null || request.getName().isBlank() ||
            request.getEmail() == null || request.getEmail().isBlank() ||
            request.getPassword() == null || request.getPassword().isBlank() ||
            request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("All fields (name, email, password, phone) are required and must not be blank");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Phone already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(Role.CLIENT);
        user.setStatus(Status.ACTIVE);

        User saved = userRepository.save(user);
        invalidateUserCaches(null);

        Map<String, Object> payload = userPayload(saved);
        emitAfterCommit("REGISTERED", payload);
        publishAfterCommit(() -> eventPublisher.publishUserRegistered(
                saved.getId(), saved.getEmail(), saved.getRole().name()));

        String token = jwtService.issue(saved.getEmail(), saved.getId(), saved.getRole().name());
        return new AuthResponse(token, jwtService.getExpirationMs());
    }

    // ── S1-F11: Login ────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        // a) Find user by email – return 401 if not found (prevents enumeration)
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }

        // b) Verify BCrypt password – return 401 if mismatch
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }

        // c) Log LOGGED_IN event to auth_events via Observer
        Map<String, Object> payload = userPayload(user);
        notifyObservers("LOGGED_IN", payload);

        // d) Issue JWT with email (sub), uid, role claims
        String token = jwtService.issue(user.getEmail(), user.getId(), user.getRole().name());

        // e) Return token + expiration with 200
        return new AuthResponse(token, jwtService.getExpirationMs());
    }

    public User save(User user) {
        String pw = user.getPassword();
        if (pw != null && !pw.startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(pw));
        }
        User saved = userRepository.save(user);
        invalidateUserCaches(null);
        emitAfterCommit("USER_CREATED", userPayload(saved));
        return saved;
    }

    /** Update an existing user's profile fields (TC20/TC22). */
    @Transactional
    public User updateUser(Long id, User updatedUser) {
        User existing = userRepository.findById(id).orElse(null);
        if (existing == null) {
            throw new RuntimeException("User not found");
        }

        if (updatedUser.getName() != null) existing.setName(updatedUser.getName());
        if (updatedUser.getEmail() != null) existing.setEmail(updatedUser.getEmail());
        if (updatedUser.getPhone() != null) existing.setPhone(updatedUser.getPhone());
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }
        // Do NOT allow role/status changes via the general update endpoint

        User saved = userRepository.save(existing);
        invalidateUserCaches(id);
        emitAfterCommit("USER_UPDATED", userPayload(saved));
        return saved;
    }

    public User updateUserPreferences(Long userId, Map<String, Object> updatedPreferences) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Map<String, Object> userPreferences = user.getPreferences() == null
                ? new HashMap<>()
                : new HashMap<>(user.getPreferences());
        if (updatedPreferences != null) {
            for (String key : updatedPreferences.keySet()) {
                userPreferences.put(key, updatedPreferences.get(key));
            }
        }
        user.setPreferences(userPreferences);

        User saved = userRepository.save(user);
        invalidateUserCaches(userId);
        Map<String, Object> payload = userPayload(saved);
        payload.put("preferences", saved.getPreferences());
        emitAfterCommit("USER_UPDATED", payload);
        return saved;
    }

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
        User saved = userRepository.save(user);
        invalidateUserCaches(userId);
        Map<String, Object> payload = userPayload(saved);
        payload.put("addressId", addressId);
        emitAfterCommit("DEFAULT_ADDRESS_SET", payload);
        return saved;
    }

    @Transactional
    public User deactivateUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // TC333: Reject if already deactivated
        if (user.getStatus() == Status.DEACTIVATED) {
            throw new IllegalStateException("User is already deactivated");
        }

        int activeBookings;
        try {
            activeBookings = bookingServiceClient.getActiveBookingCount(userId);
        } catch (FeignException e) {
            log.warn("booking-service unavailable for active count of user {}: {}", userId, e.getMessage());
            throw new ServiceUnavailableException("Booking service temporarily unavailable");
        }

        if (activeBookings > 0) {
            throw new IllegalStateException("User has active bookings");
        }

        user.setStatus(Status.DEACTIVATED);

        User saved = userRepository.save(user);
        invalidateUserCaches(userId);
        emitAfterCommit("USER_DEACTIVATED", userPayload(saved));
        publishAfterCommit(() -> eventPublisher.publishUserDeactivated(saved.getId()));
        return saved;
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Map<String, Object> payload = userPayload(user);
        userRepository.delete(user);
        invalidateUserCaches(userId);
        emitAfterCommit("USER_DELETED", payload);
    }

    // CC-2: Change user role (ADMIN-only, gated by SecurityConfig)
    public User changeRole(Long id, Role newRole) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);

        // Explicit entity-detail cache eviction (§4.4.4)
        invalidateUserCaches(id);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", id);
        payload.put("oldRole", oldRole != null ? oldRole.name() : null);
        payload.put("newRole", newRole.name());
        // Observer writes auth_events to Mongo and triggers S1-F12::* wildcard deletion
        emitAfterCommit("ROLE_CHANGED", payload);

        return saved;
    }

    // ── reads (cached) ───────────────────────────────────────────────────────

    public List<User> findAll() {
        return userRepository.findAll();
    }

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). */
    @Cacheable(cacheNames = "user-service::user", key = "#id")
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    /** S1-F1: user profile with addresses — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "user-service::S1-F1", key = "#userId")
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

    /** S1-F3: search users by name/email/role — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "user-service::S1-F3",
               key = "T(java.util.Objects).hash(#name, #email, #role)")
    public List<User> searchUsers(String name, String email, String role) {
        String searchName = (name == null || name.trim().isEmpty()) ? null : name;
        String searchEmail = (email == null || email.trim().isEmpty()) ? null : email;
        String searchRole = (role == null || role.trim().isEmpty()) ? null : role;

        return userRepository.searchUsers(searchName, searchEmail, searchRole);
    }

    /** S1-F9: users by language preference with minimum bookings — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "user-service::S1-F9",
               key = "T(java.util.Objects).hash(#language, #minBookings)")
    public List<User> findUsersByLanguagePreferenceWithMinimumBookings(String language, long minBookings) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language must not be blank");
        }

        List<User> candidates = userRepository.findUsersByLanguagePreference(language.trim());
        List<User> result = new ArrayList<>();
        for (User user : candidates) {
            try {
                long completedCount = bookingServiceClient.getCompletedBookingCount(user.getId());
                if (completedCount >= minBookings) {
                    result.add(user);
                }
            } catch (FeignException e) {
                log.warn("booking-service unavailable for completed count of user {}: {}", user.getId(), e.getMessage());
                throw new ServiceUnavailableException("Booking service temporarily unavailable");
            }
        }
        return result;
    }

    /** S1-F3: user booking summary — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "user-service::S1-F3", key = "#userId")
    public UserBookingSummaryDTO getUserBookingSummary(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        try {
            BookingSummaryDTO summary = bookingServiceClient.getUserBookingSummary(userId);
            return new UserBookingSummaryDTO(
                    user.getId(),
                    user.getName(),
                    summary.totalBookings(),
                    summary.completedBookings(),
                    summary.cancelledBookings(),
                    summary.totalSpent(),
                    summary.averageBookingPrice()
            );
        } catch (FeignException.NotFound e) {
            return new UserBookingSummaryDTO(
                    user.getId(),
                    user.getName(),
                    0L,
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        } catch (FeignException e) {
            log.warn("booking-service unavailable for user {}: {}", userId, e.getMessage());
            throw new ServiceUnavailableException("Booking service temporarily unavailable");
        }
    }

    /** S1-F8: users by JSON preference key-value — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "user-service::S1-F8",
               key = "T(java.util.Objects).hash(#key, #value)")
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

    /** S1-F6: top clients by spending report — 10 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "user-service::S1-F6",
               key = "T(java.util.Objects).hash(#startDate, #endDate, #limit)")
    public List<TopClientDTO> getTopClientsBySpending(String startDate, String endDate, int limit) {
        validateDateRange(startDate, endDate);

        String startDateTime = startDate + " 00:00:00";
        String endDateTime = endDate + " 23:59:59";

        // userId, userName
        List<User> usersDetails = userRepository.findAll();
        List<TopClientDTO> fullRows = new ArrayList<>();
        for (User user : usersDetails) {
            try {
                Long userId = user.getId();
                String userName = user.getName();

                double totalSpent = ((Number) invoiceServiceClient.getUserInvoiceTotal(userId, startDateTime,
                        endDateTime)).doubleValue();

                BookingSummaryDTO summary = bookingServiceClient.getUserBookingSummary(userId, startDateTime,
                        endDateTime);

                Long totalCompletedBookings = summary != null
                        ? summary.completedBookings()
                        : 0L;
                fullRows.add(
                        new TopClientDTO(userId, userName, totalSpent, totalCompletedBookings));
            } catch (ClassCastException e) {
                log.warn("failed to convert retrieved user Id to Long", e);
            }
        }

        fullRows.sort((a, b) -> {
            double aTotalSpent = a.getTotalSpent();
            double bTotalSpent = b.getTotalSpent();

            if (aTotalSpent > bTotalSpent)
                return -1;
            if (aTotalSpent == bTotalSpent)
                return 0;
            return 1;
        });

        return fullRows.subList(0, Math.max(limit, fullRows.size()));
    }

    /**
     * S1-F12: user activity feed (auth events) — 5 min TTL (§4.4.1).
     */
    @Cacheable(cacheNames = "user-service::S1-F12", key = "T(java.util.Objects).hash(#id, #page, #size)")
    public UserActivityFeedDTO getUserActivityFeed(Long id, int page, int size)
            throws NotFoundException {

        User user = findById(id);
        if (user == null) {
            throw new NotFoundException();
        }

        Page<AuthEvent> pageResult = authEventRepository.findByUserIdOrderByTimestampDesc(id,
                PageRequest.of(page, size));
        List<AuthEvent> authEvents = pageResult.getContent();
        int totalElements = pageResult.getNumberOfElements();

        return UserActivityFeedDTO.build(authEvents, page, Math.max(1, Math.min(100, size)), totalElements);
    }
    // ── internal helpers ─────────────────────────────────────────────────────

    /** Invalidate entity detail + all feature caches on any user write (§4.4.4). */
    private void invalidateUserCaches(Long id) {
        if (id != null) {
            cacheInvalidator.deleteKey("user-service::user::" + id);
            cacheInvalidator.deleteKey("user-service::S1-F1::" + id);
            cacheInvalidator.deleteKey("user-service::S1-F3::" + id);
        }
        cacheInvalidator.wildcardDelete("user-service::S1-F3::*");
        cacheInvalidator.wildcardDelete("user-service::S1-F6::*");
        cacheInvalidator.wildcardDelete("user-service::S1-F8::*");
        cacheInvalidator.wildcardDelete("user-service::S1-F9::*");
        cacheInvalidator.wildcardDelete("user-service::S1-F10::*");
        cacheInvalidator.wildcardDelete("user-service::S1-F12::*");
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

    private void publishAfterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private void emitAfterCommit(String action, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyObservers(action, payload);
                }
            });
        } else {
            notifyObservers(action, payload);
        }
    }

    private Map<String, Object> userPayload(User user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole() != null ? user.getRole().name() : null);
        payload.put("status", user.getStatus() != null ? user.getStatus().name() : null);
        return payload;
    }
}
