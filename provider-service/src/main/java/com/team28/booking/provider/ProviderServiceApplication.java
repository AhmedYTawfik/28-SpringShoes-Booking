package com.team28.booking.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.team28.booking.provider",
        "com.team28.booking.contracts.feign"
})
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class ProviderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderServiceApplication.class, args);
    }

}
