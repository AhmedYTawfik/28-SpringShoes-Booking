package com.team28.booking.booking.repository;

import com.team28.booking.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query(value = "SELECT COUNT(*) FROM bookings WHERE provider_id = :providerId " +
            "AND appointment_date = :date AND status IN ('REQUESTED','CONFIRMED','IN_PROGRESS')",
            nativeQuery = true)
    Long countActiveBookingsByProviderAndDate(@Param("providerId") Long providerId, @Param("date") LocalDate date);

    @Modifying
    @Query(value = "UPDATE providers SET status = 'AVAILABLE' WHERE id = :providerId", nativeQuery = true)
    void updateProviderStatusToAvailable(@Param("providerId") Long providerId);

    @Query(value = "SELECT * FROM bookings WHERE metadata->>:key = :value", nativeQuery = true)
    List<Booking> findByMetadataKeyValue(@Param("key") String key, @Param("value") String value);

    @Modifying
    @Query(value = "INSERT INTO invoices (booking_id, user_id, amount, method, status, created_at) " +
            "VALUES (:bookingId, :userId, :amount, 'CASH', 'PENDING', NOW())", nativeQuery = true)
    void createInvoiceForBooking(@Param("bookingId") Long bookingId,
                                  @Param("userId") Long userId,
                                  @Param("amount") java.math.BigDecimal amount);
}
