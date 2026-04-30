package com.team28.booking.booking.auth.handlers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Loads the user record from the shared PostgreSQL users table via JdbcTemplate.
 * Non-user services do not have the User JPA entity on the classpath (verbatim copy strategy — §1.5).
 */
public class UserLoaderHandler extends AuthHandler {

    private final JdbcTemplate jdbcTemplate;

    public UserLoaderHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void handle(AuthContext context) {
        Long uid = context.getUidClaim();
        if (uid == null) {
            context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var results = jdbcTemplate.queryForList(
                "SELECT id, role FROM users WHERE id = ?", uid);

        if (results.isEmpty()) {
            context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        context.setAuthenticatedUser(results.get(0));
        handleNext(context);
    }
}
