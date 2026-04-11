package com.team28.booking.provider.controller;

import com.team28.booking.provider.dto.UpdateAvailabilityRequest;
import com.team28.booking.provider.dto.ProviderEarningsDTO;
import com.team28.booking.provider.dto.VerifiedBy;
import com.team28.booking.provider.model.Provider;
import com.team28.booking.provider.service.ProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping
    public ResponseEntity<Provider> createProvider(@RequestBody Provider provider) {
        Provider createdProvider = providerService.createProvider(provider);
        return ResponseEntity.ok(createdProvider);
    }

    @GetMapping
    public ResponseEntity<List<Provider>> getAllProviders() {
        return ResponseEntity.ok(providerService.getAllProviders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Provider> getProviderById(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getProviderById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Provider> updateProvider(@PathVariable Long id,
                                                   @RequestBody Provider provider) {
        return ResponseEntity.ok(providerService.updateProvider(id, provider));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.ok("Provider deleted successfully");
    }

    @PutMapping("/{id}/availability")
    public ResponseEntity<Void> updateAvailability(@PathVariable Long id,
                                                   @RequestBody UpdateAvailabilityRequest request) {
        providerService.updateAvailability(id, request.status());
        return ResponseEntity.ok().build();
    }
  
    @GetMapping("/{id}/earnings")
    public ResponseEntity<ProviderEarningsDTO> getProviderEarningsSummary(
            @PathVariable Long id,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(providerService.getProviderEarningsSummary(id, startDate, endDate));
    }
  
    @PutMapping("/{id}/service-details")
    public ResponseEntity<Provider> updateServiceDetails(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(providerService.updateServiceDetails(id, updates));
    }
      
    @GetMapping("/search")
    public ResponseEntity<List<Provider>> searchProviders(
            @RequestParam(required = false) Provider.ProviderStatus status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating) {
        return ResponseEntity.ok(providerService.searchProviders(status, minRating, maxRating));
    }

    @GetMapping("/pricing-tier")
    public List<Provider> filterByPricingTier(
            @RequestParam String tier,
            @RequestParam(required = false) Provider.ProviderStatus status
    ) {
        return providerService.filterByPricingTier(tier, status);
    }

    @PutMapping("/{providerId}/certifications/{certificationId}/verify")
    public Provider verifyCertificate(
        @PathVariable Long providerId, @PathVariable Long certificationId,
        @RequestBody VerifiedBy verifiedBy
    ) {
        return providerService.verifyCertificate(providerId, certificationId, verifiedBy);
    }
}
