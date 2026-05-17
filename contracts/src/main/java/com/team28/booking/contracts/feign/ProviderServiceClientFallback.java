package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.ProviderAvailabilityDTO;
import com.team28.booking.contracts.dto.ProviderDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class ProviderServiceClientFallback implements ProviderServiceClient {
    @Override
    public ProviderDTO getProvider(Long id) {
        return new ProviderDTO(id, null, "Unavailable provider", "UNKNOWN", "UNAVAILABLE", 0.0, 0, BigDecimal.ZERO, Map.of("fallback", true));
    }

    @Override
    public ProviderAvailabilityDTO getProviderAvailability(Long id) {
        return new ProviderAvailabilityDTO("UNAVAILABLE");
    }
}
