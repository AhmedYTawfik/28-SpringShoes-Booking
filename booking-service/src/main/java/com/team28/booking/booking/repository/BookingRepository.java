package com.team28.booking.booking.repository;

import com.team28.booking.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

        @Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
                        "AND appointment_date = :date AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS')", nativeQuery = true)
        Long countActiveBookingsByProviderAndDate(@Param("providerId") Long providerId, @Param("date") LocalDate date);

        @Query(value = "SELECT * FROM bookings WHERE metadata->>:key = :value", nativeQuery = true)
        List<Booking> findByMetadataKeyValue(@Param("key") String key, @Param("value") String value);

        @Query(value = "SELECT * FROM bookings WHERE " +
                        "(:status IS NULL OR status = :status) AND " +
                        "requested_at >= :startDate AND requested_at <= :endDate " +
                        "ORDER BY requested_at DESC", nativeQuery = true)
        List<Booking> searchBookingsByStatusAndDate(
                        @Param("status") String status,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query(value = "SELECT " +
                        "COUNT(*) as totalBookings, " +
                        "COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completedBookings, " +
                        "COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) as cancelledBookings, " +
                        "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_price END), 0) as totalRevenue, " +
                        "COALESCE(AVG(CASE WHEN status = 'COMPLETED' THEN total_price END), 0) as averageBookingPrice "
                        +
                        "FROM bookings WHERE requested_at >= :startDate AND requested_at <= :endDate", nativeQuery = true)
        Object[] getBookingAnalytics(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query(value = "SELECT status, COUNT(*) as cnt FROM bookings " +
                        "WHERE requested_at >= :startDate AND requested_at <= :endDate " +
                        "GROUP BY status", nativeQuery = true)
        List<Object[]> getDashboardStatusBreakdown(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // JPQL: fetch saga-completed booking IDs for the dashboard Feign batch call
        @Query("SELECT b.id FROM Booking b WHERE b.status IN :statuses " +
                        "AND b.requestedAt >= :start AND b.requestedAt <= :end")
        List<Long> findIdsByStatusInAndDateRange(
                        @Param("statuses") List<Booking.Status> statuses,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        // ── New Feign-callable read endpoints ────────────────────────────────────

        // User booking summary: total, saga-completed, cancelled, totalSpent, avgPrice
        @Query(value = "SELECT COUNT(*), " +
                        "COUNT(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN 1 END), "
                        +
                        "COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END), " +
                        "COALESCE(SUM(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN total_price END),0), "
                        +
                        "COALESCE(AVG(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN total_price END),0) "
                        +
                        "FROM bookings WHERE user_id = :userId", nativeQuery = true)
        Object[] getUserBookingSummary(@Param("userId") Long userId);

    // User booking summary with optional date range — same aggregation filtered by appointment_date
    @Query(value = "SELECT COUNT(*), " +
            "COUNT(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN 1 END), " +
            "COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END), " +
            "COALESCE(SUM(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN total_price END),0), " +
            "COALESCE(AVG(CASE WHEN status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED','COMPLETED') THEN total_price END),0) " +
            "FROM bookings WHERE user_id = :userId " +
            "AND (:startDate IS NULL OR appointment_date >= CAST(:startDate AS date)) " +
            "AND (:endDate IS NULL OR appointment_date <= CAST(:endDate AS date))", nativeQuery = true)
    Object[] getUserBookingSummary(@Param("userId") Long userId,
                                   @Param("startDate") String startDate,
                                   @Param("endDate") String endDate);

        // Active booking count for a user: REQUESTED, CONFIRMED, IN_PROGRESS,
        // COMPLETING, PAYMENT_PENDING
        @Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId " +
                        "AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS','COMPLETING','PAYMENT_PENDING')", nativeQuery = true)
        int countActiveByUserId(@Param("userId") Long userId);

        // Completed booking count for a user: COMPLETING, PAYMENT_PENDING, PAID,
        // REFUNDED
        @Query(value = "SELECT COUNT(*) FROM bookings WHERE user_id = :userId " +
                        "AND status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED')", nativeQuery = true)
        long countCompletedByUserId(@Param("userId") Long userId);

        // Provider booking summary with optional date range — PAID bookings only
        @Query(value = "SELECT COUNT(*), COALESCE(SUM(total_price),0), COALESCE(AVG(total_price),0) " +
                        "FROM bookings WHERE provider_id = :providerId AND status = 'PAID' " +
                        "AND (:startDate IS NULL OR appointment_date >= CAST(:startDate AS date)) " +
                        "AND (:endDate IS NULL OR appointment_date <= CAST(:endDate AS date))", nativeQuery = true)
        Object[] getProviderBookingSummary(@Param("providerId") Long providerId,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate);

        // Active booking count for a provider: CONFIRMED, IN_PROGRESS, COMPLETING,
        // PAYMENT_PENDING
        @Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
                        "AND status IN ('CONFIRMED','IN_PROGRESS','COMPLETING','PAYMENT_PENDING')", nativeQuery = true)
        int countActiveByProviderId(@Param("providerId") Long providerId);

        // Completed booking count for a provider: COMPLETING, PAYMENT_PENDING, PAID,
        // REFUNDED
        @Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
                        "AND status IN ('COMPLETING','PAYMENT_PENDING','PAID','REFUNDED')", nativeQuery = true)
        long countCompletedByProviderId(@Param("providerId") Long providerId);

        // S3-EVENTS: atomic payment saga status transitions (idempotency: WHERE status
        // IN (...))
        @Modifying
        @Query(value = "UPDATE bookings SET status = 'PAYMENT_PENDING' WHERE id = :bookingId " +
                        "AND status IN ('COMPLETING','COMPLETED')", nativeQuery = true)
        int updateStatusToPaymentPending(@Param("bookingId") Long bookingId);

        @Modifying
        @Query(value = "UPDATE bookings SET status = 'PAID' WHERE id = :bookingId " +
                        "AND status = 'PAYMENT_PENDING'", nativeQuery = true)
        int updateStatusToPaid(@Param("bookingId") Long bookingId);

        @Modifying
        @Query(value = "UPDATE bookings SET status = 'PAYMENT_FAILED' WHERE id = :bookingId " +
                        "AND status = 'PAYMENT_PENDING'", nativeQuery = true)
        int updateStatusToPaymentFailed(@Param("bookingId") Long bookingId);

        @Modifying
        @Query(value = "UPDATE bookings SET status = 'REFUNDED' WHERE id = :bookingId " +
                        "AND status = 'PAYMENT_FAILED'", nativeQuery = true)
        int updateStatusToRefunded(@Param("bookingId") Long bookingId);
}
