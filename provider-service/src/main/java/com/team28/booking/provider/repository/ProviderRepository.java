package com.team28.booking.provider.repository;

import com.team28.booking.provider.dto.BookingSummary;
import com.team28.booking.provider.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query(value = """
        SELECT * FROM providers
        WHERE service_details ->> 'pricingTier' = :tier
    """,nativeQuery = true)
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
}
