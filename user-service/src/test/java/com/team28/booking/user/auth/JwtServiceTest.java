package com.team28.booking.user.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private AnnotationConfigApplicationContext applicationContext;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.registerBean(JwtService.class);
        applicationContext.refresh();
        jwtService = applicationContext.getBean(JwtService.class);
    }

    @AfterEach
    void tearDown() {
        applicationContext.close();
    }

    @Test
    void issuesAndParsesTokenWithRequiredClaims() {
        String token = jwtService.issue("client@example.com", 42L, "CLIENT");

        Claims claims = jwtService.parse(token);

        assertEquals("client@example.com", claims.getSubject());
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
        assertEquals("CLIENT", claims.get("role"));
        assertTrue(claims.getIssuedAt().before(claims.getExpiration()));
        assertEquals(JwtConfigurationManager.getInstance().getExpirationMs(), jwtService.getExpirationMs());
    }

    @Test
    void configurationManagerIsNotSpringManaged() {
        assertThrows(
                NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(JwtConfigurationManager.class)
        );
    }
}
