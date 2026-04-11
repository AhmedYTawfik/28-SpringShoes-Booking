package com.team28.booking.provider.controller;

import com.team28.booking.provider.model.ProviderCertification;
import com.team28.booking.provider.service.ProviderCertificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderCertificationController {
    private final ProviderCertificationService providerCertificationService;

    public ProviderCertificationController(ProviderCertificationService providerCertificationService) {
        this.providerCertificationService = providerCertificationService;
    }

    @PostMapping("/{providerId}/certifications")
    public ResponseEntity<ProviderCertification> createCertification(@PathVariable Long providerId,
                                                                     @RequestBody ProviderCertification certification) {
        ProviderCertification createdCertification =
                providerCertificationService.createCertification(providerId, certification);
        return ResponseEntity.ok(createdCertification);
    }

    @GetMapping("/certifications")
    public ResponseEntity<List<ProviderCertification>> getAllCertifications() {
        return ResponseEntity.ok(providerCertificationService.getAllCertifications());
    }

    @GetMapping("/certifications/{id}")
    public ResponseEntity<ProviderCertification> getCertificationById(@PathVariable Long id) {
        return ResponseEntity.ok(providerCertificationService.getCertificationById(id));
    }

    @PutMapping("/certifications/{id}")
    public ResponseEntity<ProviderCertification> updateCertification(@PathVariable Long id,
                                                                     @RequestBody ProviderCertification certification) {
        return ResponseEntity.ok(providerCertificationService.updateCertification(id, certification));
    }

    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<String> deleteCertification(@PathVariable Long id) {
        providerCertificationService.deleteCertification(id);
        return ResponseEntity.ok("Certification deleted successfully");
    }
}
