package com.team28.booking.provider.service;

import com.team28.booking.provider.cache.CacheInvalidator;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationService {

    private final CacheInvalidator cacheInvalidator;

    public CacheInvalidationService(CacheInvalidator cacheInvalidator) {
        this.cacheInvalidator = cacheInvalidator;
    }

    public void invalidateProviderRating(Long providerId) {
        cacheInvalidator.deleteKey("provider-service::provider::" + providerId);
        cacheInvalidator.wildcardDelete("provider-service::S2-F1::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F3::*");
        cacheInvalidator.wildcardDelete("provider-service::S2-F5::*");
    }

    public void invalidateProviderSearch() {
        cacheInvalidator.wildcardDelete("provider-service::S2-F10::*");
    }
}
