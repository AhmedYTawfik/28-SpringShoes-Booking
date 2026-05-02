package com.team28.booking.provider.service;

import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.observer.MongoEventLogger;
import com.team28.booking.provider.observer.Observable;
import com.team28.booking.provider.search.ProviderSearchDocument;
import com.team28.booking.provider.search.ProviderSearchRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexingService extends Observable {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final ProviderSearchRepository providerSearchRepository;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidationService cacheInvalidationService;

    public IndexingService(ProviderSearchRepository providerSearchRepository,
                           MongoEventLogger mongoEventLogger,
                           CacheInvalidationService cacheInvalidationService) {
        this.providerSearchRepository = providerSearchRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @PostConstruct
    void registerObservers() {
        register(mongoEventLogger);
    }

    public void indexProvider(Provider provider, String source) {
        try {
            providerSearchRepository.save(toDocument(provider));
            cacheInvalidationService.invalidateProviderSearch();
            Map<String, Object> payload = providerPayload(provider, source);
            emitAfterCommit("INDEXED", payload);
        } catch (Exception ex) {
            log.warn("Failed to auto-index provider {}: {}", provider.getId(), ex.getMessage());
        }
    }

    public void deleteProvider(Provider provider) {
        try {
            providerSearchRepository.deleteById(provider.getId().toString());
            cacheInvalidationService.invalidateProviderSearch();
        } catch (Exception ex) {
            log.warn("Failed to remove provider {} from Elasticsearch: {}", provider.getId(), ex.getMessage());
        } finally {
            emitAfterCommit("PROVIDER_DELETED", providerPayload(provider, "auto_crud_delete"));
        }
    }

    private ProviderSearchDocument toDocument(Provider provider) {
        Map<String, Object> serviceDetails = provider.getServiceDetails();
        Object pricingTier = serviceDetails != null ? serviceDetails.get("pricingTier") : null;

        return new ProviderSearchDocument(
                provider.getId().toString(),
                provider.getName(),
                provider.getSpecialty(),
                pricingTier != null ? pricingTier.toString() : null,
                ProviderService.descriptionOrEmpty(serviceDetails),
                provider.getRating(),
                provider.getStatus() != null ? provider.getStatus().name() : null
        );
    }

    private static final List<String> INDEXED_FIELDS =
            List.of("id", "name", "specialty", "pricingTier", "description", "rating", "status");

    private Map<String, Object> providerPayload(Provider provider, String source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("providerId", provider.getId());
        payload.put("indexedFields", INDEXED_FIELDS);
        payload.put("source", source);
        return payload;
    }

    private void emitAfterCommit(String action, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyObservers(action, payload);
                }
            });
        } else {
            notifyObservers(action, payload);
        }
    }
}
