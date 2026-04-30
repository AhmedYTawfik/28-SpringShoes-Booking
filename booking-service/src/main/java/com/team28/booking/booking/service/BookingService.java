package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.BookingEstimateRequestDTO;
import com.team28.booking.booking.dto.EstimateServiceItemDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.observer.MongoEventLogger;
import com.team28.booking.booking.observer.Observable;
import com.team28.booking.booking.repository.BookingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BookingService extends Observable {

    private final BookingRepository bookingRepository;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidator cacheInvalidator;

    public BookingService(BookingRepository bookingRepository,
                          MongoEventLogger mongoEventLogger,
                          CacheInvalidator cacheInvalidator) {
        this.bookingRepository = bookingRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidator = cacheInvalidator;
    }

    @PostConstruct
    void registerObservers() {
        register(mongoEventLogger);
    }

    // ── writes ───────────────────────────────────────────────────────────────

    public Booking createBooking(Booking booking) {
        booking.setId(null);
        Booking saved = bookingRepository.save(booking);
        // invalidate estimate + metadata-search caches; results depend on booking counts (§4.4.4)
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F5::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        emitAfterCommit("BOOKING_CREATED", bookingPayload(saved));
        return saved;
    }

    public Booking updateBooking(Long id, Booking updated) {
        Booking existing = findById(id);
        Long previousProviderId = existing.getProviderId();
        Booking.Status previousStatus = existing.getStatus();

        if (updated.getUserId() != null) existing.setUserId(updated.getUserId());
        existing.setProviderId(updated.getProviderId());
        if (updated.getAppointmentDate() != null) existing.setAppointmentDate(updated.getAppointmentDate());
        if (updated.getStartTime() != null) existing.setStartTime(updated.getStartTime());
        if (updated.getEndTime() != null) existing.setEndTime(updated.getEndTime());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        existing.setTotalPrice(updated.getTotalPrice());
        if (updated.getMetadata() != null) existing.setMetadata(updated.getMetadata());
        existing.setCompletedAt(updated.getCompletedAt());

        Booking saved = bookingRepository.save(existing);
        // invalidate the entity detail + all feature caches that include booking data (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F5::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");

        if (saved.getProviderId() != null && !Objects.equals(previousProviderId, saved.getProviderId())) {
            emitAfterCommit("PROVIDER_ASSIGNED", bookingPayload(saved));
        }
        if (saved.getStatus() == Booking.Status.COMPLETED && previousStatus != Booking.Status.COMPLETED) {
            emitAfterCommit("BOOKING_COMPLETED", bookingPayload(saved));
        }
        return saved;
    }

    public void deleteBooking(Long id) {
        Booking booking = findById(id);
        Map<String, Object> payload = bookingPayload(booking);
        bookingRepository.delete(booking);
        // invalidate entity detail + all feature caches (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F5::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        emitAfterCommit("BOOKING_DELETED", payload);
    }

    @Transactional
    public Booking cancelBooking(Long id) {
        Booking booking = findById(id);

        if (booking.getStatus() != Booking.Status.REQUESTED && booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking can only be cancelled if it is REQUESTED or CONFIRMED");
        }

        booking.setStatus(Booking.Status.CANCELLED);

        if (booking.getProviderId() != null) {
            bookingRepository.updateProviderStatusToAvailable(booking.getProviderId());
        }

        Booking saved = bookingRepository.save(booking);
        // invalidate entity detail + analytics / estimate caches (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        emitAfterCommit("BOOKING_CANCELLED", bookingPayload(saved));
        return saved;
    }

    // ── reads (cached) ───────────────────────────────────────────────────────

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). List endpoint NOT cached (§4.4.2). */
    @Cacheable(cacheNames = "booking-service::booking", key = "#id")
    @Transactional(readOnly = true)
    public Booking getBookingById(Long id) {
        return findById(id);
    }

    /** S3-F5: JSONB metadata query — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "booking-service::S3-F5",
               key = "T(java.util.Objects).hash(#key, #value)")
    @Transactional(readOnly = true)
    public List<Booking> searchByMetadata(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata value must not be blank");
        }
        return bookingRepository.findByMetadataKeyValue(key, value);
    }

    /** S3-F3: POST price estimate — 5 min TTL, keyed by request fields (§4.4.1). */
    @Cacheable(cacheNames = "booking-service::S3-F3",
               key = "T(java.util.Objects).hash(#request.providerId, #request.appointmentDate, #request.services)")
    public BookingEstimateDTO getEstimate(BookingEstimateRequestDTO request) {
        if (request.services() == null || request.services().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Services list must not be empty");
        }
        for (EstimateServiceItemDTO service : request.services()) {
            if (service.duration() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service duration must be positive");
            }
        }

        int totalDuration = request.services().stream()
                .mapToInt(EstimateServiceItemDTO::duration)
                .sum();

        BigDecimal basePrice = BigDecimal.valueOf(5.0).multiply(BigDecimal.valueOf(totalDuration));

        Long activeCount = bookingRepository.countActiveBookingsByProviderAndDate(
                request.providerId(), request.appointmentDate());

        BigDecimal demandMultiplier;
        if (activeCount <= 3) {
            demandMultiplier = BigDecimal.valueOf(1.0);
        } else if (activeCount <= 7) {
            demandMultiplier = BigDecimal.valueOf(1.25);
        } else {
            demandMultiplier = BigDecimal.valueOf(1.5);
        }

        BigDecimal estimatedPrice = basePrice.multiply(demandMultiplier);

        return BookingEstimateDTO.builder()
                .totalDuration(totalDuration)
                .basePrice(basePrice)
                .estimatedPrice(estimatedPrice)
                .demandMultiplier(demandMultiplier)
                .build();
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    /** Non-cached DB fetch — used by all write paths to guarantee fresh state. */
    Booking findById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: " + id));
    }

    void emitServicesAdded(Booking booking, Long bookingItemId) {
        Map<String, Object> payload = bookingPayload(booking);
        payload.put("bookingItemId", bookingItemId);
        emitAfterCommit("SERVICES_ADDED", payload);
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

    private Map<String, Object> bookingPayload(Booking booking) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("userId", booking.getUserId());
        payload.put("providerId", booking.getProviderId());
        payload.put("status", booking.getStatus() != null ? booking.getStatus().name() : null);
        payload.put("totalPrice", booking.getTotalPrice());
        return payload;
    }
}
