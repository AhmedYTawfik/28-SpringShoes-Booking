package com.team28.booking.provider.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

}
