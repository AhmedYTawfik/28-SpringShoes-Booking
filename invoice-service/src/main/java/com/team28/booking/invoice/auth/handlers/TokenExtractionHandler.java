package com.team28.booking.invoice.auth.handlers;

import jakarta.servlet.http.HttpServletResponse;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    public void handle(AuthContext context) {
        String authorization = context.getRequest().getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        context.setRawToken(authorization.substring(7));
        handleNext(context);
    }
}
