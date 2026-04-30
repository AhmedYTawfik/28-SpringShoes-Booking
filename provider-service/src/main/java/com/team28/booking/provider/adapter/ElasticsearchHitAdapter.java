package com.team28.booking.provider.adapter;

import com.team28.booking.provider.search.ProviderSearchDocument;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Component;

/**
 * DP-7 Adapter: converts an Elasticsearch SearchHit to ProviderSearchDocument.
 */
@Component
public class ElasticsearchHitAdapter {

    public ProviderSearchDocument adapt(SearchHit<ProviderSearchDocument> hit) {
        if (hit == null) return null;
        return hit.getContent();
    }
}
