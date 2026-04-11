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
    @Query("""
    SELECT DISTINCT p
    FROM Provider p
    JOIN FETCH p.providerCertifications c
    WHERE c.expiryDate < :now
    """)
    List<Provider> findProvidersWithExpiredCerts(@Param("now") LocalDate now);
}
