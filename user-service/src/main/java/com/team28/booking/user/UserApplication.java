package com.team28.booking.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.team28.booking.user",
        "com.team28.booking.contracts.feign"
})
@EnableFeignClients(basePackages = "com.team28.booking.contracts.feign")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
//
//    @Bean
//    public CommandLineRunner checkBeans(ApplicationContext ctx) {
//        return args -> {
//            System.out.println("===== BEANS LOADED =====");
//            String[] beanNames = ctx.getBeanDefinitionNames();
//            for (String beanName : beanNames) {
//                if (beanName.contains("Controller") || beanName.contains("Service") || beanName.contains("Repository")) {
//                    System.out.println(beanName);
//                }
//            }
//            System.out.println("========================");
//        };
//    }
//}
