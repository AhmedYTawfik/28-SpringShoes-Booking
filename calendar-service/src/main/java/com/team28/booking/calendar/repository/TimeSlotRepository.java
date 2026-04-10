package com.team28.booking.calendar.repository;

import com.team28.booking.calendar.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported=false)
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    @Query(value = "SELECT COUNT(*) FROM providers WHERE id = :providerId", nativeQuery = true)
    Long countProviderById(@Param("providerId") Long providerId);

    Optional<TimeSlot> findTopByProviderIdOrderByDateDescStartTimeDesc(Long providerId);

    @Query(value = """
            SELECT p.id AS providerId, p.name AS providerName, p.specialty,
                   p.rating, COUNT(ts.id) AS availableSlots
            FROM time_slots ts
            JOIN providers p ON ts.provider_id = p.id
            WHERE ts.date = :date
              AND ts.available = true
              AND (:specialty IS NULL OR p.specialty = :specialty)
            GROUP BY p.id, p.name, p.specialty, p.rating
            ORDER BY p.rating DESC
            """, nativeQuery = true)
    List<Object[]> findAvailableProvidersByDate(
            @Param("date") LocalDate date,
            @Param("specialty") String specialty);
}
