package com.team28.booking.provider.repository;

import com.team28.booking.provider.model.Provider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query("SELECT p FROM Provider p ORDER BY p.rating DESC")
    List<Provider> findTopProviders(Pageable pageable);
}
