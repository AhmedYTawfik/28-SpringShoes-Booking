package com.team28.booking.user.auth.handlers;

import com.team28.booking.user.auth.JwtService;
import com.team28.booking.user.model.User;
import com.team28.booking.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthHandlerChainTest {

    @Test
    void authHandlerExposesSetNextAndHandleContract() throws Exception {
        assertEquals(AuthHandler.class, AuthHandler.class.getDeclaredMethod("setNext", AuthHandler.class).getReturnType());
        assertNotNull(AuthHandler.class.getDeclaredMethod("handle", AuthContext.class));

        assertInstanceOf(AuthHandler.class, new TokenExtractionHandler());
        assertInstanceOf(AuthHandler.class, new SignatureValidationHandler(new JwtService()));
        assertInstanceOf(AuthHandler.class, new UserLoaderHandler(mock(UserRepository.class)));
        assertInstanceOf(AuthHandler.class, new RoleAuthorizationHandler());
    }

    @Test
    void tokenExtractionShortCircuitsWhenAuthorizationHeaderIsMissing() {
        AuthHandler next = mock(AuthHandler.class);
        TokenExtractionHandler handler = new TokenExtractionHandler();
        handler.setNext(next);

        HttpServletRequest request = mock(HttpServletRequest.class);
        AuthContext context = new AuthContext(request);

        handler.handle(context);

        assertTrue(context.hasError());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, context.getErrorStatus());
        verifyNoInteractions(next);
    }

    @Test
    void roleAuthorizationRejectsClientRoleForAdminRoute() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getServletPath()).thenReturn("/api/users/12/role");

        Claims claims = Jwts.claims()
                .add("uid", 12L)
                .add("role", "CLIENT")
                .build();
        AuthContext context = new AuthContext(request);
        context.setClaims(claims);

        new RoleAuthorizationHandler().handle(context);

        assertTrue(context.hasError());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, context.getErrorStatus());
    }

    @Test
    void userLoaderLoadsUserByUidClaim() {
        User user = new User();
        user.setId(7L);
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(7L)).thenReturn(Optional.of(user));

        AuthContext context = new AuthContext(mock(HttpServletRequest.class));
        context.setClaims(Jwts.claims().add("uid", 7L).add("role", "CLIENT").build());

        new UserLoaderHandler(repository).handle(context);

        assertFalse(context.hasError());
        assertEquals(user, context.getAuthenticatedUser());
        verify(repository).findById(7L);
    }
}
