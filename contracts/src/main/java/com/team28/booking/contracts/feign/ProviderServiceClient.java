package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.ProviderAvailabilityDTO;
import com.team28.booking.contracts.dto.ProviderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "provider-service", url = "${feign.provider-service.url}", fallback = ProviderServiceClientFallback.class)
public interface ProviderServiceClient {
    @GetMapping("/api/providers/{id}")
    ProviderDTO getProvider(@PathVariable("id") Long id);

    @GetMapping("/api/providers/{id}/availability")
    ProviderAvailabilityDTO getProviderAvailability(@PathVariable("id") Long id);
}
