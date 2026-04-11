package com.team28.booking.provider.repository;

import com.team28.booking.provider.dto.BookingSummary;
import com.team28.booking.provider.dto.ProviderSummary;
import com.team28.booking.provider.model.Provider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query(value = """
        SELECT p.*, COUNT(b.id) as booking_count
        FROM providers p
        INNER JOIN bookings b ON b.provider_id = p.id
        GROUP BY p.id
        ORDER BY p.rating DESC
    """, nativeQuery = true)
    List<ProviderSummary> findTopProvidersWithBookingCount(Pageable pageable);
           
    @Query("""
      SELECT DISTINCT p
      FROM Provider p
      JOIN FETCH p.providerCertifications c
      WHERE c.expiryDate < :now
    """)
    List<Provider> findProvidersWithExpiredCerts(@Param("now") LocalDate now);
           
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
        SELECT id, provider_id, status FROM bookings
        WHERE id = :bookingId
    """, nativeQuery = true)
    Optional<BookingSummary> getBookingSummary(@Param("bookingId") Long bookingId);
           
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
}
