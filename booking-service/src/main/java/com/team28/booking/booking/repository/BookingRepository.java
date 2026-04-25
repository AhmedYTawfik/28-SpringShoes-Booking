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

    @Query(value = "SELECT COUNT(*) FROM providers WHERE id = :providerId", nativeQuery = true)
    Long countProvidersById(@Param("providerId") Long providerId);

    @Query(value = "SELECT status FROM providers WHERE id = :providerId", nativeQuery = true)
    String findProviderStatusById(@Param("providerId") Long providerId);

    // Cross-service write via shared PostgreSQL — no HTTP calls between services.
    // @Transactional is required on @Modifying methods; without it Spring throws TransactionRequiredException
    // if the caller is ever non-transactional. The service already provides a transaction, but annotating
    // here makes each method self-contained and safe regardless of the call site.
    @Modifying
    @Transactional
    @Query(value = "UPDATE providers SET status = :status WHERE id = :providerId", nativeQuery = true)
    void updateProviderStatus(@Param("providerId") Long providerId, @Param("status") String status);

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
    @Query(value = "SELECT " +
            "COUNT(*) as totalBookings, " +
            "COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completedBookings, " +
            "COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) as cancelledBookings, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_price END), 0) as totalRevenue, " +
            "COALESCE(AVG(CASE WHEN status = 'COMPLETED' THEN total_price END), 0) as averageBookingPrice " +
            "FROM bookings WHERE requested_at >= :startDate AND requested_at <= :endDate", nativeQuery = true)
    Object[] getBookingAnalytics(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);
}
