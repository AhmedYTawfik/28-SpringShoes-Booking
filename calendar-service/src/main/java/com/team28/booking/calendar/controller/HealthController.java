package com.team28.booking.calendar.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeslots")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

}
