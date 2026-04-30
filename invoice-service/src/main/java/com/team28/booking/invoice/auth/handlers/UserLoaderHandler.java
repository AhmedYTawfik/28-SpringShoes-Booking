package com.team28.booking.invoice.auth.handlers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;

public class UserLoaderHandler extends AuthHandler {
    private final JdbcTemplate jdbcTemplate;

    public UserLoaderHandler(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void handle(AuthContext context) {
        Long uid = context.getUidClaim();
        if (uid == null) { context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED); return; }
        var results = jdbcTemplate.queryForList("SELECT id, role FROM users WHERE id = ?", uid);
        if (results.isEmpty()) { context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED); return; }
        context.setAuthenticatedUser(results.get(0));
        handleNext(context);
    }
}
