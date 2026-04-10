package com.team28.booking.provider.repository;

import com.team28.booking.provider.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
