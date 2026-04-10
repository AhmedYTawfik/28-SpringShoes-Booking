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

}
