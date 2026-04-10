package com.team28.booking.booking.repository;

import com.team28.booking.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
            "AND appointment_date = :date AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS')",
            nativeQuery = true)
    Long countActiveBookingsByProviderAndDate(@Param("providerId") Long providerId, @Param("date") LocalDate date);
}
