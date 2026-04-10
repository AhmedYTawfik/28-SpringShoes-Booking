package com.team28.booking.calendar.repository;

import com.team28.booking.calendar.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    @Query(value = "SELECT COUNT(*) FROM providers WHERE id = :providerId", nativeQuery = true)
    Long countProviderById(@Param("providerId") Long providerId);

    Optional<TimeSlot> findTopByProviderIdOrderByDateDescStartTimeDesc(Long providerId);
}
