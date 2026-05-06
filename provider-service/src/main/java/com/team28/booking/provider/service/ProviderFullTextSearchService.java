package com.team28.booking.provider.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.team28.booking.provider.adapter.ElasticsearchHitAdapter;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.search.ProviderSearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderFullTextSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProviderFullTextSearchService.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchHitAdapter elasticsearchHitAdapter;

    public ProviderFullTextSearchService(ElasticsearchOperations elasticsearchOperations,
                                         ElasticsearchHitAdapter elasticsearchHitAdapter) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchHitAdapter = elasticsearchHitAdapter;
    }

    @Cacheable(cacheNames = "provider-service::S2-F10",
               key = "T(java.util.Objects).hash(#query, #specialty, #pricingTier, #status, #minRating, #maxRating)")
    public List<ProviderSearchDocument> search(String query,
                                               String specialty,
                                               String pricingTier,
                                               Provider.ProviderStatus status,
                                               Double minRating,
                                               Double maxRating) {
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRating cannot be greater than maxRating");
        }
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();

            if (query != null && !query.isBlank()) {
                bool.should(Query.of(q -> q.match(m -> m.field("name").query(query))));
                bool.should(Query.of(q -> q.match(m -> m.field("description").query(query))));
                bool.minimumShouldMatch("1");
            }

            if (specialty != null && !specialty.isBlank()) {
                bool.filter(Query.of(q -> q.term(t -> t.field("specialty").value(specialty))));
            }
            if (pricingTier != null && !pricingTier.isBlank()) {
                bool.filter(Query.of(q -> q.term(t -> t.field("pricingTier").value(pricingTier))));
            }
            if (status != null) {
                String statusStr = status.name();
                bool.filter(Query.of(q -> q.term(t -> t.field("status").value(statusStr))));
            }

            if (minRating != null || maxRating != null) {
                final Double min = minRating;
                final Double max = maxRating;

                bool.filter(Query.of(q -> q.range(r -> r.number(n -> {
                    n.field("rating");

                    if (min != null) {
                        n.gte(min);
                    }

                    if (max != null) {
                        n.lte(max);
                    }

                    return n;
                }))));
            }

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.bool(bool.build())))
                    .build();

            SearchHits<ProviderSearchDocument> hits =
                    elasticsearchOperations.search(searchQuery, ProviderSearchDocument.class);

            return hits.stream()
                    .map(elasticsearchHitAdapter::adapt)
                    .toList();

        } catch (Exception ex) {
            log.warn("Full-text search failed (ES unavailable or index missing): {}", ex.getMessage());
            return List.of();
        }
    }
}
