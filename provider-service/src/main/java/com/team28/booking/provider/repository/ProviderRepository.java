package com.team28.booking.provider.repository;

import com.team28.booking.provider.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query(value = "SELECT * FROM providers WHERE service_details ->> 'pricingTier' = :tier", nativeQuery = true)
    List<Provider> findByTier(@Param("tier") String tier);

    @Query(value = """
        SELECT * FROM providers
        WHERE status = :status
        AND service_details ->> 'pricingTier' = :tier
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
        ON b.invoices_id = i.id
        WHERE b.status = 'COMPLETED'
        AND b.providerId = :providerId
    """, nativeQuery = true)
    Object[] getProviderDashboardSummary(@Param("providerId") Long id);

    @Query(value = """
        SELECT
            COALESCE(
                SUM(CASE WHEN ts.available = false THEN 1 ELSE 0 END) * 1.0
                / NULLIF(COUNT(*), 0),
                0.0
            ) AS utilization_rate
        FROM time_slots ts
        WHERE ts.provider_id = :providerId
        AND ts.start_time >= DATE_TRUNC('month', CURRENT_DATE)
        AND ts.start_time < DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month'
    """, nativeQuery = true)
    Double getUtilizationRate(@Param("providerId") Long id);
}
