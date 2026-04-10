package com.team28.booking.calendar.repository;

import com.team28.booking.calendar.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.repository.query.Param;

import java.util.List;

@RepositoryRestResource(exported=false)
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    @Query(value = "SELECT * FROM time_slots WHERE metadata ->> :key = :value", nativeQuery = true)
    List<TimeSlot> findByMetadataEquals(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM time_slots WHERE CAST(metadata ->> :key AS NUMERIC) > CAST(:value AS NUMERIC)",
            nativeQuery = true)
    List<TimeSlot> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT * FROM time_slots WHERE CAST(metadata ->> :key AS NUMERIC) < CAST(:value AS NUMERIC)",
            nativeQuery = true)
    List<TimeSlot> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);
}
