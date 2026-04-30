package com.team28.booking.user.mongo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {

    Page<AuthEvent> findByUserId(Long userId, Pageable pageable);

    Page<AuthEvent> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
}
