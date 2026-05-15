package com.team28.booking.invoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class InvoiceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceServiceApplication.class, args);
    }

}
