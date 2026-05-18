package com.team28.booking.invoice.auth;

import com.team28.booking.invoice.auth.handlers.*;
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
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthHandler chainHead;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        AuthHandler ext = new TokenExtractionHandler();
        AuthHandler sig = new SignatureValidationHandler(jwtService);
        AuthHandler usr = new UserLoaderHandler(jdbcTemplate);
        AuthHandler rol = new RoleAuthorizationHandler();
        ext.setNext(sig).setNext(usr).setNext(rol);
        this.chainHead = ext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublic(request.getServletPath())) { filterChain.doFilter(request, response); return; }

        String xUserId = request.getHeader("X-User-Id");
        String xUserRole = request.getHeader("X-User-Role");
        if (xUserId != null && !xUserId.isBlank() && xUserRole != null && !xUserRole.isBlank()) {
            var auth = new UsernamePasswordAuthenticationToken(
                Map.of("id", Long.valueOf(xUserId), "role", xUserRole), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + xUserRole)));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try { filterChain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
            return;
        }

        AuthContext context = new AuthContext(request);
        chainHead.handle(context);
        if (context.hasError()) { response.setStatus(context.getErrorStatus()); return; }
        var auth = new UsernamePasswordAuthenticationToken(
            context.getAuthenticatedUser(), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + context.getRoleClaim())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try { filterChain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
    }

    private boolean isPublic(String path) {
        return path.equals("/api/auth/register") || path.equals("/api/auth/login")
                || path.startsWith("/actuator/health");
    }
}
