package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.dto.BookingAnalyticsDTO;
import com.team28.booking.booking.dto.BookingAnalyticsDashboardDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.BookingEstimateRequestDTO;
import com.team28.booking.booking.dto.EstimateServiceItemDTO;
import com.team28.booking.booking.dto.ServiceDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.observer.MongoEventLogger;
import com.team28.booking.booking.observer.Observable;
import com.team28.booking.booking.repository.BookingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class BookingService extends Observable {

    private final BookingRepository bookingRepository;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidator cacheInvalidator;
    private final CacheManager cacheManager;

    public BookingService(BookingRepository bookingRepository,
                          MongoEventLogger mongoEventLogger,
                          CacheInvalidator cacheInvalidator,
                          CacheManager cacheManager) {
        this.bookingRepository = bookingRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidator = cacheInvalidator;
        this.cacheManager = cacheManager;
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
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F5::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
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
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F5::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
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
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F5::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
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
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
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

    /** S3-F4: complete an IN_PROGRESS booking, release provider, create PENDING invoice. */
    @Transactional
    public Booking completeBooking(Long id) {
        Booking booking = findById(id);

        if (booking.getStatus() != Booking.Status.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking must be IN_PROGRESS to complete");
        }

        booking.setStatus(Booking.Status.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());

        if (booking.getTotalPrice() == null) {
            BigDecimal total = Optional.ofNullable(booking.getBookingServices())
                    .orElse(List.of())
                    .stream()
                    .map(BookingItem::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            booking.setTotalPrice(total);
        }

        if (booking.getProviderId() != null) {
            bookingRepository.updateProviderStatusToAvailable(booking.getProviderId());
        }

        bookingRepository.createInvoiceForBooking(
                booking.getId(), booking.getUserId(), booking.getTotalPrice());

        Booking saved = bookingRepository.save(booking);
        cacheInvalidator.deleteKey("booking-service::booking::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        emitAfterCommit("BOOKING_COMPLETED", bookingPayload(saved));
        return saved;
    }

    /** S3-F1: search bookings by optional status and date range — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "booking-service::S3-F1",
               key = "T(java.util.Objects).hash(#status, #startDate, #endDate)")
    @Transactional(readOnly = true)
    public List<Booking> searchBookings(String status, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        return bookingRepository.searchBookingsByStatusAndDate(status, startDateTime, endDateTime);
    }

    /** S3-F6: booking analytics report over a date range. */
    @Cacheable(cacheNames = "booking-service::S3-F6",
               key = "T(java.util.Objects).hash(#startDate, #endDate)")
    @Transactional(readOnly = true)
    public BookingAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be on or before endDate");
        }
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Object[] result = bookingRepository.getBookingAnalytics(startDateTime, endDateTime);

        Object[] row = (result.length > 0 && result[0] instanceof Object[])
                ? (Object[]) result[0]
                : result;

        long totalBookings     = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long completedBookings = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long cancelledBookings = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        BigDecimal totalRevenue        = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
        BigDecimal averageBookingPrice = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

        double completionRate = totalBookings > 0
                ? ((double) completedBookings / totalBookings) * 100.0
                : 0.0;

        return new BookingAnalyticsDTO(totalBookings, completedBookings, cancelledBookings,
                totalRevenue, averageBookingPrice, completionRate);
    }

    /**
     * S3-F10: booking analytics dashboard over a date range — 10 min TTL (§4.4.1).
     * MongoDB ANALYTICS_VIEWED log is written outside the cache layer so it fires on every
     * call, including cache hits (spec §10.3.1 d).
     */
    public BookingAnalyticsDashboardDTO getDashboardAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be on or before endDate");
        }

        // Log ANALYTICS_VIEWED on every invocation — outside cache, per spec §10.3.1 d
        logAnalyticsViewed(startDate, endDate);

        // Programmatic cache check — avoids AOP self-invocation limitation
        String cacheKey = String.valueOf(Objects.hash(startDate, endDate));
        Cache cache = cacheManager.getCache("booking-service::S3-F10");
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null) {
                return (BookingAnalyticsDashboardDTO) wrapper.get();
            }
        }

        BookingAnalyticsDashboardDTO result = computeDashboardAnalytics(startDate, endDate);

        if (cache != null) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    @Transactional(readOnly = true)
    BookingAnalyticsDashboardDTO computeDashboardAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Object[] agg = bookingRepository.getDashboardAggregates(startDateTime, endDateTime);
        Object[] row = (agg.length > 0 && agg[0] instanceof Object[]) ? (Object[]) agg[0] : agg;

        long totalBookings    = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        BigDecimal totalRevenue       = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
        BigDecimal averageBookingValue = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;

        List<Object[]> statusRows = bookingRepository.getDashboardStatusBreakdown(startDateTime, endDateTime);
        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        long completedCount = 0L;
        for (Object[] statusRow : statusRows) {
            String status = (String) statusRow[0];
            long count = ((Number) statusRow[1]).longValue();
            bookingsByStatus.put(status, count);
            if ("COMPLETED".equals(status)) {
                completedCount = count;
            }
        }

        double completionRate = totalBookings > 0 ? (double) completedCount / totalBookings : 0.0;

        return BookingAnalyticsDashboardDTO.builder()
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .averageBookingValue(averageBookingValue)
                .completionRate(completionRate)
                .bookingsByStatus(bookingsByStatus)
                .build();
    }

    private void logAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> params = Map.of("action", "ANALYTICS_VIEWED",
                "startDate", startDate.toString(),
                "endDate", endDate.toString());
        mongoEventLogger.onEvent("ANALYTICS_VIEWED", params);
    }

    /** S3-F9: booking detail with sorted service items and completion summary. */
    @Cacheable(cacheNames = "booking-service::S3-F9",
               key = "#id")
    @Transactional(readOnly = true)
    public BookingDetailsDTO getBookingDetails(Long id) {
        Booking booking = findById(id);

        List<ServiceDetailsDTO> services = Optional.ofNullable(booking.getBookingServices())
                .orElse(List.of())
                .stream()
                .sorted(Comparator.comparing(BookingItem::getServiceOrder))
                .map(item -> new ServiceDetailsDTO(
                        item.getId(),
                        item.getServiceOrder(),
                        item.getServiceName(),
                        item.getDuration(),
                        item.getPrice(),
                        item.getStatus(),
                        item.getMetadata()))
                .toList();

        int totalServices     = services.size();
        int completedServices = (int) services.stream()
                .filter(s -> s.status() == BookingItem.Status.COMPLETED)
                .count();

        return new BookingDetailsDTO(
                booking.getId(),
                booking.getUserId(),
                booking.getProviderId(),
                booking.getStatus(),
                booking.getTotalPrice(),
                booking.getMetadata(),
                services,
                totalServices,
                completedServices);
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
