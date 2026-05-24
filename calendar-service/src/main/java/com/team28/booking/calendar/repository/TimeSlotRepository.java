package com.team28.booking.calendar.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.transaction.annotation.Transactional;

import com.team28.booking.calendar.model.TimeSlot;

@RepositoryRestResource(exported=false)
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Optional<TimeSlot> findTopByProviderIdOrderByDateDescStartTimeDesc(Long providerId);

    Optional<TimeSlot> findByProviderIdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThan(Long providerId, LocalDate date, LocalTime startTime, LocalTime endTime);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE time_slots SET available = false
            WHERE provider_id = :providerId AND date = :date AND start_time = :startTime
            AND available = true
            """, nativeQuery = true)
    int reserveSlot(@Param("providerId") Long providerId,
                    @Param("date") LocalDate date,
                    @Param("startTime") LocalTime startTime);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE time_slots SET available = true
            WHERE provider_id = :providerId AND date = :date AND start_time = :startTime
            AND available = false
            """, nativeQuery = true)
    int releaseSlot(@Param("providerId") Long providerId,
                    @Param("date") LocalDate date,
                    @Param("startTime") LocalTime startTime);

    @Query(value = """
            SELECT provider_id AS providerId, COUNT(*) AS availableSlots
            FROM time_slots
            WHERE date = :date
              AND available = true
            GROUP BY provider_id
            """, nativeQuery = true)
    List<Object[]> countAvailableSlotsByProviderAndDate(@Param("date") LocalDate date);

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

    @Query(value = """
            SELECT provider_id AS providerId,
                   COUNT(*) FILTER (WHERE available = false) AS bookedSlotsCount,
                   COUNT(*) AS totalSlotsCount
            FROM time_slots
            WHERE date >= :sinceDate
            GROUP BY provider_id
            HAVING COUNT(*) FILTER (WHERE available = false) <= :maxBookedSlots
            ORDER BY provider_id
            """, nativeQuery = true)
    List<Object[]> findIdleProviderIds(
            @Param("maxBookedSlots") int maxBookedSlots,
            @Param("sinceDate") LocalDate sinceDate);
           
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
           
    @Query(value = "SELECT COUNT(*) FROM time_slots WHERE date < :cutoffDate", nativeQuery = true)
    int countByDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM time_slots WHERE date < :cutoffDate", nativeQuery = true)
    int deleteByDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Query(value = """
            SELECT COUNT(*) FROM time_slots 
            WHERE provider_id = :providerId 
              AND date = :date 
              AND (start_time < :endTime AND end_time > :startTime)
              AND (:id IS NULL OR id != :id)
            """, nativeQuery = true)
    long countOverlappingSlots(@Param("providerId") Long providerId, 
                               @Param("date") LocalDate date, 
                               @Param("startTime") java.time.LocalTime startTime, 
                               @Param("endTime") java.time.LocalTime endTime,
                               @Param("id") Long id);

    // ── S4-F10: Calendar Analytics Dashboard ─────────────────────────────────

    @Query(value = """
            SELECT
                COUNT(*) AS totalSlots,
                COUNT(*) FILTER (WHERE available = true) AS availableSlots,
                COUNT(*) FILTER (WHERE available = false) AS bookedSlots
            FROM time_slots
            WHERE date >= :startDate AND date <= :endDate
            """, nativeQuery = true)
    Object[] getAnalyticsStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(value = """
            SELECT CAST(date AS TEXT) AS slotDate, COUNT(*) AS cnt
            FROM time_slots
            WHERE date >= :startDate AND date <= :endDate
            GROUP BY date
            ORDER BY date
            """, nativeQuery = true)
    List<Object[]> getSlotsByDate(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // ── S4-F11: Record Provider Availability Snapshot ─────────────────────────

    @Query(value = """
            SELECT
                COUNT(*) AS totalSlots,
                COUNT(*) FILTER (WHERE available = true) AS availableSlots,
                COUNT(*) FILTER (WHERE available = false) AS bookedSlots
            FROM time_slots
            WHERE provider_id = :providerId AND date = :date
            """, nativeQuery = true)
    Object[] getSnapshotStats(
            @Param("providerId") Long providerId,
            @Param("date") LocalDate date);
}
