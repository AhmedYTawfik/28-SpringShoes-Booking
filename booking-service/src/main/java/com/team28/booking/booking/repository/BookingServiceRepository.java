package com.team28.booking.booking.repository;

import com.team28.booking.booking.model.BookingService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingServiceRepository extends JpaRepository<BookingService, Long> {
}
