package io.github.idymian.geops.identity.infrastructure;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings from the environment ({@code JWT_SECRET}, {@code JWT_ACCESS_TTL}, {@code JWT_REFRESH_TTL}).
 * The app refuses to start with a missing or weak secret.
 */
@ConfigurationProperties(prefix = "geops.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {

    static final int MIN_SECRET_BYTES = 32; // 256 bits, required for HS256

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. Generate one with: openssl rand -base64 64");
        }
        if (secretBytes(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be base64 and at least 256 bits long.");
        }
        accessTtl = accessTtl == null ? Duration.ofMinutes(15) : accessTtl;
        refreshTtl = refreshTtl == null ? Duration.ofDays(7) : refreshTtl;
    }

    public byte[] secretBytes() {
        return secretBytes(secret);
    }

    private static byte[] secretBytes(String secret) {
        try {
            return Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be valid base64.", e);
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl + "]";
    }
}
