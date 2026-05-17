package com.team28.booking.contracts.feign;

import com.team28.booking.contracts.dto.UserDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class UserServiceClientFallback implements UserServiceClient {
    @Override
    public UserDTO getUser(Long id) {
        return new UserDTO(id, "Unavailable user", null, "UNKNOWN", "UNAVAILABLE", null, Map.of("fallback", true), LocalDateTime.now());
    }
}
