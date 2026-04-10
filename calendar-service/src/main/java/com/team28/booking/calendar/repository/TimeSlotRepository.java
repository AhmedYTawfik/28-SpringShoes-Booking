package com.team28.booking.calendar.repository;

import com.team28.booking.calendar.dto.IdleProviderProjection;
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
            SELECT p.id AS providerId,
                   p.name AS providerName,
                   p.specialty,
                   p.rating,
                   COALESCE(booked.cnt, 0) AS bookedSlotsCount,
                   COUNT(ts.id) AS totalSlotsCount
            FROM providers p
            LEFT JOIN time_slots ts ON ts.provider_id = p.id AND ts.date >= :sinceDate
            LEFT JOIN (
                SELECT provider_id, COUNT(*) AS cnt
                FROM time_slots
                WHERE available = false AND date >= :sinceDate
                GROUP BY provider_id
            ) booked ON booked.provider_id = p.id
            GROUP BY p.id, p.name, p.specialty, p.rating, booked.cnt
            HAVING COALESCE(booked.cnt, 0) <= :maxBookedSlots
            ORDER BY p.id
            """, nativeQuery = true)
    List<IdleProviderProjection> findIdleProviders(
            @Param("maxBookedSlots") int maxBookedSlots,
            @Param("sinceDate") LocalDate sinceDate);
}
