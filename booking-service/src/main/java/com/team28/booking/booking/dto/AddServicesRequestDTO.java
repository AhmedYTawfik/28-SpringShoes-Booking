package com.team28.booking.booking.dto;

import java.util.List;

public record AddServicesRequestDTO(List<ServiceItemDTO> services) {
    public record ServiceItemDTO(String serviceName, int duration, double price) {}
}
