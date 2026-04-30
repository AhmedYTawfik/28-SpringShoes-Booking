package com.team28.booking.provider.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderEventRepository extends MongoRepository<ProviderEvent, String> {

    List<ProviderEvent> findByProviderId(Long providerId);

    List<ProviderEvent> findByAction(String action);
}
