package com.team28.booking.invoice.auth.handlers;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

public class AuthContext {
    private final HttpServletRequest request;
    private String rawToken;
    private Claims claims;
    private Object authenticatedUser;
    private Integer errorStatus;

    public AuthContext(HttpServletRequest request) { this.request = request; }
    public HttpServletRequest getRequest() { return request; }
    public String getRawToken() { return rawToken; }
    public void setRawToken(String rawToken) { this.rawToken = rawToken; }
    public Claims getClaims() { return claims; }
    public void setClaims(Claims claims) { this.claims = claims; }
    public Object getAuthenticatedUser() { return authenticatedUser; }
    public void setAuthenticatedUser(Object authenticatedUser) { this.authenticatedUser = authenticatedUser; }
    public boolean hasError() { return errorStatus != null; }
    public int getErrorStatus() { return errorStatus == null ? 0 : errorStatus; }
    public void setErrorStatus(int errorStatus) { this.errorStatus = errorStatus; }
    public String getRoleClaim() { return claims == null ? null : claims.get("role", String.class); }
    public Long getUidClaim() {
        if (claims == null || claims.get("uid") == null) return null;
        return ((Number) claims.get("uid")).longValue();
    }
}
