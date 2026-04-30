package com.team28.booking.provider.auth.handlers;

import com.team28.booking.provider.auth.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void handle(AuthContext context) {
        try {
            context.setClaims(jwtService.parse(context.getRawToken()));
        } catch (JwtException | IllegalArgumentException e) {
            context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        handleNext(context);
    }
}
