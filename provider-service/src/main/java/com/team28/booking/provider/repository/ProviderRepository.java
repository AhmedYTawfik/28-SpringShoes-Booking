package com.team28.booking.provider.repository;

import com.team28.booking.provider.dto.BookingSummary;
import com.team28.booking.provider.dto.ProviderRepoDashboardReturn;
import com.team28.booking.provider.dto.ProviderSummary;
import com.team28.booking.provider.model.Provider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query(value = """
        SELECT * FROM providers
        WHERE service_details ->> 'tier' = :tier
    """,nativeQuery = true)
    List<Provider> findByTier(@Param("tier") String tier);

    @Query(value = """
        SELECT * FROM providers
        WHERE status = :status
        AND service_details ->> 'tier' = :tier
    """, nativeQuery = true)
    List<Provider> findByTierAndStatus(
            @Param("tier") String tier,
            @Param("status") String status
    );

    @Query(value = """
    SELECT COUNT(*)
    FROM bookings b
    WHERE b.provider_id = :providerId
      AND b.status NOT IN ('COMPLETED', 'CANCELLED')
    """, nativeQuery = true)
    Long countActiveBookings(@Param("providerId") Long providerId);

    //it wasn't clear in the pdf so i assumed we will filter according appointmentDate not completedAt
    @Query(value = """
    SELECT 
        COUNT(*) AS total_bookings,
        COALESCE(SUM(b.total_price), 0),
        COALESCE(AVG(b.total_price), 0)
    FROM bookings b
    WHERE b.provider_id = :providerId
      AND b.status = 'COMPLETED'
      AND b.appointment_date BETWEEN :startDate AND :endDate
    """, nativeQuery = true)
    List<Object[]> getProviderEarningsSummary(@Param("providerId") Long providerId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    //could have done it without jpql but will not be the best if status is null
    @Query("""
        SELECT p
        FROM Provider p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:minRating IS NULL OR p.rating >= :minRating)
          AND (:maxRating IS NULL OR p.rating <= :maxRating)
        ORDER BY p.rating DESC
    """)
    List<Provider> searchProviders(
            @Param("status") Provider.ProviderStatus status,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating
    );

    @Query(value = """
        SELECT
            COUNT(*) AS total_completed,
            SUM(i.amount) AS total_revenue,
            AVG(i.amount) AS average_booking_val
        FROM bookings b
        JOIN invoices i
        ON i.booking_id = b.id
        WHERE b.status = 'COMPLETED'
        AND b.provider_id = :providerId
    """, nativeQuery = true)
    ProviderRepoDashboardReturn getProviderDashboardSummary(@Param("providerId") Long id);

    @Query(value = """
        SELECT
            COALESCE(
                SUM(CASE WHEN ts.available = false THEN 1 ELSE 0 END) * 1.0
                / NULLIF(COUNT(*), 0),
                0.0
            ) AS utilization_rate
        FROM time_slots ts
        WHERE ts.provider_id = :providerId
        AND ts.date >= DATE_TRUNC('month', CURRENT_DATE)::date
        AND ts.date < (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month')::date
    """, nativeQuery = true)
    Double getUtilizationRate(@Param("providerId") Long id);


    @Query("""
    SELECT DISTINCT p
    FROM Provider p
    JOIN FETCH p.providerCertifications c
    WHERE c.expiryDate < :now
    """)
    List<Provider> findProvidersWithExpiredCerts(@Param("now") LocalDate now);

    @Query(value = """
        SELECT id, provider_id, status FROM bookings
        WHERE id = :bookingId
    """, nativeQuery = true)
    Optional<BookingSummary> getBookingSummary(@Param("bookingId") Long bookingId);

    /**
     * Atomically mark a booking as rated using the metadata JSONB field.
     * Returns 1 if marked (first rating), 0 if already rated (duplicate).
     */
    @Modifying
    @Query(value = """
        UPDATE bookings
        SET metadata = COALESCE(metadata, '{}'::jsonb) || '{"rated": true}'::jsonb
        WHERE id = :bookingId
          AND (metadata IS NULL OR NOT jsonb_exists(metadata, 'rated'))
    """, nativeQuery = true)
    int markBookingAsRated(@Param("bookingId") Long bookingId);


    @Query(value = """
        SELECT p.*, COUNT(b.id) as booking_count
        FROM providers p
        LEFT JOIN bookings b ON b.provider_id = p.id
        GROUP BY p.id
        ORDER BY p.rating DESC
    """, nativeQuery = true)
    List<ProviderSummary> findTopProvidersWithBookingCount(Pageable pageable);
}
