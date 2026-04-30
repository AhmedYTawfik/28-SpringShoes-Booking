package com.team28.booking.user.config;

import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        User admin = new User();
        admin.setName("Admin User");
        admin.setEmail("admin@springshoes.com");
        admin.setPassword(passwordEncoder.encode("Admin@1234"));
        admin.setPhone("01000000001");
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        User client1 = new User();
        client1.setName("Alice Smith");
        client1.setEmail("alice@springshoes.com");
        client1.setPassword(passwordEncoder.encode("Alice@1234"));
        client1.setPhone("01000000002");
        client1.setRole(User.Role.CLIENT);
        userRepository.save(client1);

        User client2 = new User();
        client2.setName("Bob Jones");
        client2.setEmail("bob@springshoes.com");
        client2.setPassword(passwordEncoder.encode("Bob@1234"));
        client2.setPhone("01000000003");
        client2.setRole(User.Role.CLIENT);
        userRepository.save(client2);
    }
}