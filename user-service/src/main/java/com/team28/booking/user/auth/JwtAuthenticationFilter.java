package com.team28.booking.user.auth;

import com.team28.booking.user.auth.handlers.AuthContext;
import com.team28.booking.user.auth.handlers.AuthHandler;
import com.team28.booking.user.auth.handlers.RoleAuthorizationHandler;
import com.team28.booking.user.auth.handlers.SignatureValidationHandler;
import com.team28.booking.user.auth.handlers.TokenExtractionHandler;
import com.team28.booking.user.auth.handlers.UserLoaderHandler;
import com.team28.booking.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthHandler chainHead;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        AuthHandler extractionHandler = new TokenExtractionHandler();
        AuthHandler signatureHandler = new SignatureValidationHandler(jwtService);
        AuthHandler userLoaderHandler = new UserLoaderHandler(userRepository);
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

        String xUserId = request.getHeader("X-User-Id");
        String xUserRole = request.getHeader("X-User-Role");
        if (xUserId != null && !xUserId.isBlank() && xUserRole != null && !xUserRole.isBlank()) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    Map.of("id", Long.valueOf(xUserId), "role", xUserRole),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + xUserRole))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
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
                || path.startsWith("/actuator/health");
    }
}
