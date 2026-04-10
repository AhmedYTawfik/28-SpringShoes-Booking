package com.team28.booking.provider.repository;

import com.team28.booking.provider.model.ProviderCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderCertificationRepository extends JpaRepository<ProviderCertification, Long> {
    // THis is a really naive way to do it
    @Query(value = """
        SELECT EXISTS (
        SELECT 1 FROM users WHERE id = :id AND role = 'ADMIN'
        )""", nativeQuery = true)
    boolean verifyVerifierIsAdmin(@Param("id") Long id);
}
