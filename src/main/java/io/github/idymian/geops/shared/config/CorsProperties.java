package io.github.idymian.geops.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit list of origins allowed to call the API from a browser.
 * Set through {@code CORS_ALLOWED_ORIGINS} as a comma-separated list; never "*".
 */
@ConfigurationProperties(prefix = "geops.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
