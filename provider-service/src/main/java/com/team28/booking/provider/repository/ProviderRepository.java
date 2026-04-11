package com.team28.booking.provider.repository;

import com.team28.booking.provider.dto.ProviderSummary;
import com.team28.booking.provider.model.Provider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
