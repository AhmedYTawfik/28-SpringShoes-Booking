package com.team28.booking.calendar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.team28.booking.calendar",
        "com.team28.booking.contracts.feign"
})
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class CalendarServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendarServiceApplication.class, args);
    }

}
