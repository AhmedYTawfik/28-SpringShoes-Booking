package com.team28.booking.booking.repository;

import com.team28.booking.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Modifying
    @Query(value = "UPDATE providers SET status = 'AVAILABLE' WHERE id = :providerId", nativeQuery = true)
    void updateProviderStatusToAvailable(@Param("providerId") Long providerId);
}
