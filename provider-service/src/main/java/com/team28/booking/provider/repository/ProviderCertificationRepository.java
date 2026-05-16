package com.team28.booking.provider.repository;

import com.team28.booking.provider.model.ProviderCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderCertificationRepository extends JpaRepository<ProviderCertification, Long> {
    boolean existsByIdAndProvider_Id(Long id, Long providerId);
}
