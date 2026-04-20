package com.team28.booking.booking.repository;

import com.team28.booking.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
            "AND appointment_date = :date AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS')",
            nativeQuery = true)
    Long countActiveBookingsByProviderAndDate(@Param("providerId") Long providerId, @Param("date") LocalDate date);

    // Cross-service write via shared PostgreSQL — no HTTP calls between services.
    // @Transactional is required on @Modifying methods; without it Spring throws TransactionRequiredException
    // if the caller is ever non-transactional. The service already provides a transaction, but annotating
    // here makes each method self-contained and safe regardless of the call site.
    @Modifying
    @Transactional
    @Query(value = "UPDATE providers SET status = 'AVAILABLE' WHERE id = :providerId", nativeQuery = true)
    void updateProviderStatusToAvailable(@Param("providerId") Long providerId);

    @Query(value = "SELECT * FROM bookings WHERE metadata->>:key = :value", nativeQuery = true)
    List<Booking> findByMetadataKeyValue(@Param("key") String key, @Param("value") String value);

    // Cross-service INSERT into the invoice-service table via shared DB — no HTTP call made.
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO invoices (booking_id, user_id, amount, method, status, created_at) " +
            "VALUES (:bookingId, :userId, :amount, 'CASH', 'PENDING', NOW())", nativeQuery = true)
    void createInvoiceForBooking(@Param("bookingId") Long bookingId,
                                  @Param("userId") Long userId,
                                  @Param("amount") java.math.BigDecimal amount);

    @Query(value = "SELECT * FROM bookings WHERE " +
            "(:status IS NULL OR CAST(status AS text) = CAST(:status AS text)) AND " +
            "requested_at >= :startDate AND requested_at <= :endDate " +
            "ORDER BY requested_at DESC", nativeQuery = true)
    List<Booking> searchBookingsByStatusAndDate(
            @Param("status") String status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
}
