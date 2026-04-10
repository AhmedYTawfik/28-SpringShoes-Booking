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

    @Query(value = "SELECT * FROM time_slots WHERE metadata ->> :key = :value", nativeQuery = true)
    List<TimeSlot> findByMetadataEquals(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM time_slots WHERE CAST(metadata ->> :key AS NUMERIC) > CAST(:value AS NUMERIC)",
            nativeQuery = true)
    List<TimeSlot> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM time_slots WHERE CAST(metadata ->> :key AS NUMERIC) < CAST(:value AS NUMERIC)",
            nativeQuery = true)
    List<TimeSlot> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);

    @Query(value = """
            SELECT
                COUNT(*) AS totalSlots,
                COUNT(*) FILTER (WHERE available = false) AS bookedSlots,
                COUNT(*) FILTER (WHERE available = true) AS availableSlots
            FROM time_slots
            WHERE provider_id = :providerId
              AND date >= :startDate AND date <= :endDate
            """, nativeQuery = true)
    Object[] getUtilizationStats(
            @Param("providerId") Long providerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(value = """
            SELECT TO_CHAR(date, 'Day') AS dayOfWeek
            FROM time_slots
            WHERE provider_id = :providerId
              AND date >= :startDate AND date <= :endDate
              AND available = false
            GROUP BY TO_CHAR(date, 'Day')
            ORDER BY COUNT(*) DESC
            LIMIT 1
            """, nativeQuery = true)
    String findPeakDay(
            @Param("providerId") Long providerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
