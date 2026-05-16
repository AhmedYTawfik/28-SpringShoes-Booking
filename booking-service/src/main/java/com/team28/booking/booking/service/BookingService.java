package com.team28.booking.booking.service;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.messaging.BookingEventPublisher;
import com.team28.booking.booking.dto.AddServicesRequestDTO;
import com.team28.booking.booking.dto.BookingAnalyticsDTO;
import com.team28.booking.booking.dto.BookingAnalyticsDashboardDTO;
import com.team28.booking.booking.dto.BookingDetailsDTO;
import com.team28.booking.booking.dto.BookingEstimateDTO;
import com.team28.booking.booking.dto.BookingEstimateRequestDTO;
import com.team28.booking.booking.dto.EstimateServiceItemDTO;
import com.team28.booking.booking.dto.ProviderRecommendationDTO;
import com.team28.booking.booking.dto.ServiceDetailsDTO;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.model.BookingItem;
import com.team28.booking.booking.observer.MongoEventLogger;
import com.team28.booking.booking.observer.Observable;
import com.team28.booking.booking.repository.BookingItemRepository;
import com.team28.booking.booking.repository.BookingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Set;
import java.util.stream.Collectors;

import com.team28.booking.contracts.feign.CalendarServiceClient;
import com.team28.booking.contracts.feign.InvoiceServiceClient;
import com.team28.booking.contracts.feign.ProviderServiceClient;
import com.team28.booking.contracts.feign.UserServiceClient;
import com.team28.booking.contracts.dto.BookingSummaryDTO;
import com.team28.booking.contracts.dto.InvoiceAmountDTO;
import com.team28.booking.contracts.dto.InvoiceAmountsRequest;
import com.team28.booking.contracts.dto.ProviderBookingSummaryDTO;
import com.team28.booking.contracts.dto.ProviderDTO;
import com.team28.booking.contracts.dto.UserDTO;
import feign.FeignException;
import java.math.RoundingMode;

@Service
public class BookingService extends Observable {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidator cacheInvalidator;
    private final CacheManager cacheManager;
    private final Neo4jClient neo4jClient;
    private final BookingEventPublisher eventPublisher;
    private final ProviderServiceClient providerServiceClient;
    private final InvoiceServiceClient invoiceServiceClient;
    private final UserServiceClient userServiceClient;
    private final CalendarServiceClient calendarServiceClient;

    public BookingService(BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository,
                          MongoEventLogger mongoEventLogger,
                          CacheInvalidator cacheInvalidator,
                          CacheManager cacheManager,
                          Neo4jClient neo4jClient,
                          BookingEventPublisher eventPublisher,
                          ProviderServiceClient providerServiceClient,
                          InvoiceServiceClient invoiceServiceClient,
                          UserServiceClient userServiceClient,
                          CalendarServiceClient calendarServiceClient) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidator = cacheInvalidator;
        this.cacheManager = cacheManager;
        this.neo4jClient = neo4jClient;
        this.eventPublisher = eventPublisher;
        this.providerServiceClient = providerServiceClient;
        this.invoiceServiceClient = invoiceServiceClient;
        this.userServiceClient = userServiceClient;
        this.calendarServiceClient = calendarServiceClient;
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
    public Booking assignProvider(Long bookingId, Long providerId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != Booking.Status.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking must be in REQUESTED status to assign a provider");
        }
        // Validate provider exists and is AVAILABLE via Feign — no cross-DB query
        ProviderDTO provider;
        try {
            provider = providerServiceClient.getProvider(providerId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider service unavailable");
        }
        if (!"AVAILABLE".equals(provider.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is not available");
        }
        booking.setProviderId(providerId);
        booking.setStatus(Booking.Status.CONFIRMED);
        Booking saved = bookingRepository.save(booking);
        cacheInvalidator.deleteKey("booking-service::booking::" + bookingId);
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        emitAfterCommit("PROVIDER_ASSIGNED", bookingPayload(saved));
        // Publish booking.placed here (not at creation) so provider-service marks provider BUSY
        // only after assignment is confirmed
        publishAfterCommit(() -> eventPublisher.publishBookingPlaced(
                saved.getId(), saved.getUserId(), saved.getProviderId()));
        return saved;
    }

    @Transactional
    public Booking cancelBooking(Long id) {
        Booking booking = findById(id);

        if (booking.getStatus() != Booking.Status.REQUESTED && booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking can only be cancelled if it is REQUESTED or CONFIRMED");
        }

        booking.setStatus(Booking.Status.CANCELLED);

        // Provider status flip is handled asynchronously by provider-service
        // consuming the booking.cancelled event — no direct cross-DB update here
        Booking saved = bookingRepository.save(booking);
        // invalidate entity detail + analytics / estimate caches (§4.4.4)
        cacheInvalidator.deleteKey("booking-service::booking::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        emitAfterCommit("BOOKING_CANCELLED", bookingPayload(saved));
        publishAfterCommit(() -> eventPublisher.publishBookingCancelled(
                saved.getId(), saved.getUserId(), saved.getProviderId(), "user_requested"));
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
            if (service.price() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service price must not be negative");
            }
        }

        int totalDuration = request.services().stream()
                .mapToInt(EstimateServiceItemDTO::duration)
                .sum();

        BigDecimal basePrice = request.services().stream()
                .map(s -> BigDecimal.valueOf(s.price()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);


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
        // 1. ADMIN-only auth check
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can complete bookings");
        }

        // 2. Find booking
        Booking booking = findById(id);

        // 3. Status == IN_PROGRESS check
        if (booking.getStatus() != Booking.Status.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking must be IN_PROGRESS to complete");
        }

        // 4. Calculate totalPrice if null (local, from BookingItem prices)
        if (booking.getTotalPrice() == null) {
            BigDecimal total = Optional.ofNullable(booking.getBookingServices())
                    .orElse(List.of())
                    .stream()
                    .map(BookingItem::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            booking.setTotalPrice(total);
        }

        // 5. Three Feign pre-checks
        // Pre-check 1: User must be ACTIVE
        UserDTO user;
        try {
            user = userServiceClient.getUser(booking.getUserId());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found");
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not active");
        }

        // Pre-check 2: Provider must be BUSY
        ProviderDTO provider;
        try {
            provider = providerServiceClient.getProvider(booking.getProviderId());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider not found");
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider service unavailable");
        }
        if (!"BUSY".equals(provider.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is not BUSY");
        }

        // Pre-check 3: Calendar slot must exist
        try {
            calendarServiceClient.getSlotForBooking(
                    booking.getProviderId(),
                    booking.getAppointmentDate().toString(),
                    booking.getStartTime().toString());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No calendar slot found for this booking");
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Calendar service unavailable");
        }

        // 6. Set status = COMPLETING, completedAt = now(), save
        booking.setStatus(Booking.Status.COMPLETING);
        booking.setCompletedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        cacheInvalidator.deleteKey("booking-service::booking::" + id);
        cacheInvalidator.wildcardDelete("booking-service::S3-F1::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F3::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F6::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        cacheInvalidator.wildcardDelete("booking-service::S3-F10::*");
        emitAfterCommit("BOOKING_COMPLETED", bookingPayload(saved));
        double price = saved.getTotalPrice() != null ? saved.getTotalPrice().doubleValue() : 0.0;
        publishAfterCommit(() -> eventPublisher.publishBookingCompleted(
                saved.getId(), saved.getUserId(), saved.getProviderId(), price));
        return saved;
    }

    /** S3-F1: search bookings by optional status and date range — 5 min TTL (§4.4.1). */
    @Cacheable(cacheNames = "booking-service::S3-F1",
               key = "T(java.util.Objects).hash(#status, #startDate, #endDate)")
    @Transactional(readOnly = true)
    public List<Booking> searchBookings(String status, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.of(1970, 1, 1);
        if (endDate == null) endDate = LocalDate.of(9999, 12, 31);
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate");
        }
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
                ? (double) completedBookings / totalBookings
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

        // Step 1: local status breakdown — no cross-DB join
        List<Object[]> statusRows = bookingRepository.getDashboardStatusBreakdown(startDateTime, endDateTime);
        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        long totalBookings = 0L;
        for (Object[] statusRow : statusRows) {
            String status = (String) statusRow[0];
            long count = ((Number) statusRow[1]).longValue();
            bookingsByStatus.put(status, count);
            totalBookings += count;
        }

        // Step 2: collect IDs of saga-completed bookings for Feign batch
        List<Booking.Status> sagaCompleted = List.of(
                Booking.Status.COMPLETING, Booking.Status.PAYMENT_PENDING,
                Booking.Status.PAID, Booking.Status.REFUNDED);
        List<Long> completedBookingIds = bookingRepository.findIdsByStatusInAndDateRange(
                sagaCompleted, startDateTime, endDateTime);

        // Step 3: completionRate = saga-completed / total
        long completedCount = completedBookingIds.size();
        double completionRate = totalBookings > 0 ? (double) completedCount / totalBookings : 0.0;

        // Steps 4-5: batch Feign to invoice-service; skip entirely if no completed bookings
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal averageBookingValue = BigDecimal.ZERO;
        if (!completedBookingIds.isEmpty()) {
            Map<Long, InvoiceAmountDTO> invoiceMap;
            try {
                invoiceMap = invoiceServiceClient
                        .getInvoiceAmountsByBookings(new InvoiceAmountsRequest(completedBookingIds));
            } catch (FeignException e) {
                // Graceful degradation per spec §2.4: never let a downstream failure crash the caller
                log.warn("invoice-service unavailable for dashboard analytics: {}", e.getMessage());
                invoiceMap = Map.of();
            }
            if (invoiceMap != null && !invoiceMap.isEmpty()) {
                totalRevenue = invoiceMap.values().stream()
                        .map(InvoiceAmountDTO::amount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                // Denominator = bookings that actually have an invoice entry (not totalBookings)
                averageBookingValue = totalRevenue.divide(
                        BigDecimal.valueOf(invoiceMap.size()), 2, RoundingMode.HALF_UP);
            }
        }

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

    /** S3 new: user booking summary consumed by user-service via Feign (S1-F3). */
    @Transactional(readOnly = true)
    public BookingSummaryDTO getUserBookingSummary(Long userId) {
        Object[] result = bookingRepository.getUserBookingSummary(userId);
        Object[] row = (result.length > 0 && result[0] instanceof Object[]) ? (Object[]) result[0] : result;
        long total     = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long completed = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long cancelled = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        BigDecimal totalSpent = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
        BigDecimal avgPrice   = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
        return new BookingSummaryDTO(total, completed, cancelled, totalSpent, avgPrice);
    }

    /** S3 new: date-range user booking summary — consumed by user-service via Feign (S1-F3 date filter). */
    @Transactional(readOnly = true)
    public BookingSummaryDTO getUserBookingSummaryByDateRange(Long userId, String startDate, String endDate) {
        Object[] result = bookingRepository.getUserBookingSummaryByDateRange(userId, startDate, endDate);
        Object[] row = (result.length > 0 && result[0] instanceof Object[]) ? (Object[]) result[0] : result;
        long total     = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long completed = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long cancelled = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        BigDecimal totalSpent = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
        BigDecimal avgPrice   = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
        return new BookingSummaryDTO(total, completed, cancelled, totalSpent, avgPrice);
    }

    /** S3 new: active booking count for a user, consumed by user-service via Feign (S1-F4). */
    @Transactional(readOnly = true)
    public int getUserActiveCount(Long userId) {
        return bookingRepository.countActiveByUserId(userId);
    }

    /** S3 new: completed booking count for a user, consumed by user-service via Feign (S1-F9). */
    @Transactional(readOnly = true)
    public long getUserCompletedCount(Long userId) {
        return bookingRepository.countCompletedByUserId(userId);
    }

    /** S3 new: provider booking summary (PAID only, optional date range), consumed by provider-service via Feign (S2-F3). */
    @Transactional(readOnly = true)
    public ProviderBookingSummaryDTO getProviderBookingSummary(Long providerId, String startDate, String endDate) {
        Object[] result = bookingRepository.getProviderBookingSummary(providerId, startDate, endDate);
        Object[] row = (result.length > 0 && result[0] instanceof Object[]) ? (Object[]) result[0] : result;
        long total        = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        BigDecimal earned = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
        BigDecimal avg    = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
        return new ProviderBookingSummaryDTO(total, earned, avg);
    }

    /** S3 new: active booking count for a provider, consumed by provider-service via Feign (S2-F4). */
    @Transactional(readOnly = true)
    public int getProviderActiveCount(Long providerId) {
        return bookingRepository.countActiveByProviderId(providerId);
    }

    /** S3 new: completed booking count for a provider, consumed by provider-service via Feign (S2-F6). */
    @Transactional(readOnly = true)
    public long getProviderCompletedCount(Long providerId) {
        return bookingRepository.countCompletedByProviderId(providerId);
    }

    /**
     * S3-F12: Get provider recommendations for a user using Neo4j collaborative filtering.
     *
     * @param userId   the PG user ID to recommend for
     * @param limit    max number of recommendations (default 5)
     * @return ranked list of ProviderRecommendationDTO
     */
    @Cacheable(cacheNames = "booking-service::S3-F12",
               key = "T(java.util.Objects).hash(#userId, #limit)")
    @Transactional(readOnly = true)
    public List<ProviderRecommendationDTO> getRecommendations(Long userId, int limit) {
        // a) Ownership check: caller must be the user themselves or an ADMIN
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                // Extract uid claim from principal (stored as Map<String,Object> by UserLoaderHandler)
                Object principal = auth.getPrincipal();
                Long callerUid = null;
                if (principal instanceof Map<?, ?> map) {
                    Object idVal = map.get("id");
                    if (idVal != null) callerUid = ((Number) idVal).longValue();
                }
                if (callerUid == null || !callerUid.equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied: you can only view your own recommendations");
                }
            }
        }

        // c) Verify user exists via Feign
        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }

        // d) Traverse the Neo4j recommendation graph via Neo4jClient
        List<Map<String, Object>> neo4jResults = new java.util.ArrayList<>(
                neo4jClient.query(
                        "MATCH (u:User {id: $userId})-[:BOOKED]->(shared:Provider)<-[:BOOKED]-(similar:User) " +
                        "WHERE similar.id <> $userId " +
                        "MATCH (similar)-[:BOOKED]->(candidate:Provider) " +
                        "WHERE NOT (u)-[:BOOKED]->(candidate) " +
                        "RETURN candidate.id AS providerId, count(distinct similar) AS score " +
                        "ORDER BY score DESC " +
                        "LIMIT $limit")
                        .bind(userId).to("userId")
                        .bind(limit).to("limit")
                        .fetch().all());

        if (neo4jResults.isEmpty()) {
            return List.of();
        }

        // e) Collect provider IDs and enrich with Feign calls
        List<Long> providerIds = neo4jResults.stream()
                .map(r -> ((Number) r.get("providerId")).longValue())
                .collect(Collectors.toList());

        Map<Long, ProviderDTO> providerMap = new HashMap<>();
        for (Long pid : providerIds) {
            try {
                providerMap.put(pid, providerServiceClient.getProvider(pid));
            } catch (FeignException e) {
                // skip unavailable providers
            }
        }

        // f) Assemble DTOs preserving Neo4j ranking order
        return neo4jResults.stream()
                .map(r -> {
                    Long pid = ((Number) r.get("providerId")).longValue();
                    long score = ((Number) r.get("score")).longValue();
                    ProviderDTO pDto = providerMap.get(pid);
                    String name = pDto != null ? pDto.name() : "";
                    String specialty = pDto != null ? pDto.specialty() : "";
                    return ProviderRecommendationDTO.builder()
                            .providerId(pid)
                            .name(name)
                            .specialty(specialty)
                            .score(score)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * S3-F11: Record User-Provider Booking Pattern
     *
     * @param bookingId the booking to record
     */
    public void recordInteraction(Long bookingId) {
        Booking booking = findById(bookingId);

        Set<Booking.Status> allowed = Set.of(
                Booking.Status.COMPLETED, Booking.Status.COMPLETING,
                Booking.Status.PAYMENT_PENDING, Booking.Status.PAID);
        if (!allowed.contains(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be completed to record interaction");
        }

        Long userId = booking.getUserId();
        Long providerId = booking.getProviderId();

        if (providerId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking has no provider assigned");
        }

        // Fetch user and provider details for Neo4j enrichment
        UserDTO user;
        try {
            user = userServiceClient.getUser(userId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }
        ProviderDTO provider;
        try {
            provider = providerServiceClient.getProvider(providerId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider service unavailable");
        }

        // Check idempotency — skip if this bookingId was already recorded
        try {
            var rows = neo4jClient.query(
                    "OPTIONAL MATCH (u:User {id: $userId})-[r:BOOKED]->(p:Provider {id: $providerId}) " +
                    "RETURN $bookingId IN coalesce(r.recorded_booking_ids, []) AS alreadyRecorded")
                    .bind(userId).to("userId")
                    .bind(providerId).to("providerId")
                    .bind(bookingId).to("bookingId")
                    .fetch().all();
            if (!rows.isEmpty() && Boolean.TRUE.equals(rows.iterator().next().get("alreadyRecorded"))) {
                return;
            }
        } catch (Exception e) {
            // Neo4j not yet populated — proceed to create
        }

        // SDN 8.0.3 NPEs on @Query write operations via the repository layer;
        // use Neo4jClient directly so the Cypher is sent without result-type mapping.
        neo4jClient.query(
                "MERGE (u:User {id: $userId}) SET u.name = $userName " +
                "MERGE (p:Provider {id: $providerId}) SET p.name = $providerName, p.specialty = $specialty " +
                "MERGE (u)-[r:BOOKED]->(p) " +
                "ON CREATE SET r.bookingCount = 1, r.lastBookingDate = localdatetime(), r.recorded_booking_ids = [$bookingId] " +
                "ON MATCH SET " +
                "  r.bookingCount = r.bookingCount + CASE WHEN $bookingId IN coalesce(r.recorded_booking_ids, []) THEN 0 ELSE 1 END, " +
                "  r.lastBookingDate = localdatetime(), " +
                "  r.recorded_booking_ids = CASE WHEN $bookingId IN coalesce(r.recorded_booking_ids, []) THEN coalesce(r.recorded_booking_ids, []) ELSE coalesce(r.recorded_booking_ids, []) + $bookingId END")
                .bind(userId).to("userId")
                .bind(providerId).to("providerId")
                .bind(bookingId).to("bookingId")
                .bind(user.name()).to("userName")
                .bind(provider.name()).to("providerName")
                .bind(provider.specialty()).to("specialty")
                .run();

        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", bookingId);
        payload.put("userId", userId);
        payload.put("providerId", providerId);
        emitAfterCommit("INTERACTION_RECORDED", payload);
    }

    /** S3-F7: Add services to a REQUESTED or CONFIRMED booking. */
    @Transactional
    public Booking addServices(Long bookingId, AddServicesRequestDTO request) {
        if (request.services() == null || request.services().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Services list must not be empty");
        }
        Booking booking = findById(bookingId);
        if (booking.getStatus() != Booking.Status.REQUESTED &&
                booking.getStatus() != Booking.Status.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Services can only be added to REQUESTED or CONFIRMED bookings");
        }
        int currentCount = booking.getBookingServices() == null ? 0 : booking.getBookingServices().size();
        for (int i = 0; i < request.services().size(); i++) {
            AddServicesRequestDTO.ServiceItemDTO s = request.services().get(i);
            BookingItem item = new BookingItem();
            item.setServiceOrder(currentCount + i + 1);
            item.setServiceName(s.serviceName());
            item.setDuration(s.duration());
            item.setPrice(BigDecimal.valueOf(s.price()));
            item.setStatus(BookingItem.Status.PENDING);
            item.setBooking(booking);
            bookingItemRepository.save(item);
        }
        // Recompute totalPrice as sum of all booking services
        Booking refreshed = findById(bookingId);
        BigDecimal total = Optional.ofNullable(refreshed.getBookingServices())
                .orElse(List.of())
                .stream()
                .map(BookingItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        refreshed.setTotalPrice(total);
        Booking saved = bookingRepository.save(refreshed);
        cacheInvalidator.deleteKey("booking-service::booking::" + bookingId);
        cacheInvalidator.wildcardDelete("booking-service::S3-F9::*");
        emitServicesAdded(saved, null);
        return saved;
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
