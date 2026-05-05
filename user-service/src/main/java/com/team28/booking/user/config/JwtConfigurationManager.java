package com.team28.booking.user.config;

public class JwtConfigurationManager {
    private static JwtConfigurationManager instance;
    private final String secret;
    private final long expiration;

    private JwtConfigurationManager() {
        // Read from env or fallback to hardcoded dev values
        String envSecret = System.getenv("JWT_SECRET");
        this.secret = (envSecret != null) ? envSecret : "VGhpcyBpcyBhIDMyLWJ5dGUgc2VjcmV0IGtleSBmb3IgSFMyNTYhISE=";

        String envExp = System.getenv("JWT_EXPIRATION_MS");
        this.expiration = (envExp != null) ? Long.parseLong(envExp) : 86400000L;
    }

    public static synchronized JwtConfigurationManager getInstance() {
        if (instance == null) {
            instance = new JwtConfigurationManager();
        }
        return instance;
    }

    public String getSecret() { return secret; }
    public long getExpiration() { return expiration; }
}
