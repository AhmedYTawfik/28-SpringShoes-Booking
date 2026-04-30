package com.team28.booking.provider.auth;

public final class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private final String secret;
    private final long expirationMs;

    private JwtConfigurationManager() {
        String envSecret = System.getenv("JWT_SECRET");
        this.secret = (envSecret != null && !envSecret.isBlank())
                ? envSecret
                : "fJ8z2vK6yQ3pX9mR4wT5sN7bE1uI0aH8oL2cD6gV4eK3=";

        String envExpiration = System.getenv("JWT_EXPIRATION_MS");
        this.expirationMs = (envExpiration != null && !envExpiration.isBlank())
                ? Long.parseLong(envExpiration)
                : 86_400_000L;
    }

    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
