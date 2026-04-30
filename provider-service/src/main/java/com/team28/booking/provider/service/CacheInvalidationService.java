package com.team28.booking.provider.service;

import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationService {

    public void invalidateProviderRating(Long providerId) {
        // Phase 9 wires the backing cache. This hook preserves the invalidation contract now.
    }

    public void invalidateProviderSearch() {
        // Phase 9 wires provider-service::S2-F10::* invalidation behind this hook.
    }
}
