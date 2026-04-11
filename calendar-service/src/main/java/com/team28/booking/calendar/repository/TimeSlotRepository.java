package com.team28.booking.calendar.repository;

import com.team28.booking.calendar.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported=false)
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    @Query(value = "SELECT COUNT(*) FROM providers WHERE id = :providerId", nativeQuery = true)
    Long countProviderById(@Param("providerId") Long providerId);

    Optional<TimeSlot> findTopByProviderIdOrderByDateDescStartTimeDesc(Long providerId);

    @Query(value = """
            SELECT * FROM time_slots
            WHERE date >= :startDate AND date <= :endDate
              AND (:providerId IS NULL OR provider_id = :providerId)
            ORDER BY date ASC, start_time ASC
            """, nativeQuery = true)
    List<TimeSlot> findByDateRangeAndProvider(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("providerId") Long providerId);
           
    @Query(value = "SELECT * FROM time_slots WHERE metadata ->> :key = :value", nativeQuery = true)
    List<TimeSlot> findByMetadataEquals(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM time_slots WHERE CAST(metadata ->> :key AS NUMERIC) > CAST(:value AS NUMERIC)",
            nativeQuery = true)
    List<TimeSlot> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM time_slots WHERE CAST(metadata ->> :key AS NUMERIC) < CAST(:value AS NUMERIC)",
            nativeQuery = true)
    List<TimeSlot> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT COUNT(*) FROM time_slots WHERE date < :cutoffDate", nativeQuery = true)
    int countByDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM time_slots WHERE date < :cutoffDate", nativeQuery = true)
    int deleteByDateBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
