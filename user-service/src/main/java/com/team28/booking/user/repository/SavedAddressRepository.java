package com.team28.booking.user.repository;

import com.team28.booking.user.model.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {
}