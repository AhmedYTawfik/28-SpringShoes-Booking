package com.team28.booking.booking.auth;

import com.team28.booking.booking.auth.handlers.AuthContext;
import com.team28.booking.booking.auth.handlers.AuthHandler;
import com.team28.booking.booking.auth.handlers.RoleAuthorizationHandler;
import com.team28.booking.booking.auth.handlers.SignatureValidationHandler;
import com.team28.booking.booking.auth.handlers.TokenExtractionHandler;
import com.team28.booking.booking.auth.handlers.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthHandler chainHead;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        AuthHandler extractionHandler = new TokenExtractionHandler();
        AuthHandler signatureHandler = new SignatureValidationHandler(jwtService);
        AuthHandler userLoaderHandler = new UserLoaderHandler(jdbcTemplate);
        AuthHandler roleAuthorizationHandler = new RoleAuthorizationHandler();
        extractionHandler.setNext(signatureHandler).setNext(userLoaderHandler).setNext(roleAuthorizationHandler);
        this.chainHead = extractionHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublic(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthContext context = new AuthContext(request);
        chainHead.handle(context);
        if (context.hasError()) {
            response.setStatus(context.getErrorStatus());
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                context.getAuthenticatedUser(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + context.getRoleClaim()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPublic(String path) {
        return path.equals("/api/auth/register")
                || path.equals("/api/auth/login")
                || path.startsWith("/actuator/health")
                ;
    }
}
