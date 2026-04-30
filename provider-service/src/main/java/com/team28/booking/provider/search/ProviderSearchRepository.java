package com.team28.booking.provider.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderSearchRepository extends ElasticsearchRepository<ProviderSearchDocument, String> {

    List<ProviderSearchDocument> findBySpecialty(String specialty);

    List<ProviderSearchDocument> findByStatus(String status);

    List<ProviderSearchDocument> findByNameContainingOrDescriptionContaining(String name, String description);
}
