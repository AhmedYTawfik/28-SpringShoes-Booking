package com.team28.booking.calendar.auth.handlers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public void handle(AuthContext context) {
        String requiredRole = requiredRole(context);
        if (requiredRole != null && !requiredRole.equals(context.getRoleClaim())) {
            context.setErrorStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        handleNext(context);
    }

    private String requiredRole(AuthContext context) {
        String method = context.getRequest().getMethod();
        String path = context.getRequest().getServletPath();
        if (HttpMethod.PUT.matches(method) && path.matches("/api/users/[^/]+/role")) {
            return "ADMIN";
        }
        return null;
    }
}
