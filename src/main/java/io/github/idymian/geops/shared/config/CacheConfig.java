package io.github.idymian.geops.shared.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@code @Cacheable}. The Redis cache manager and its TTL come from
 * {@code spring.cache.*} in application.yml, so every API instance shares one cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
