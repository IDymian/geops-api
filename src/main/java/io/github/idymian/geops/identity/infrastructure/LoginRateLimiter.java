package io.github.idymian.geops.identity.infrastructure;

import java.time.Duration;
import java.util.Locale;

import io.github.idymian.geops.shared.error.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Limits failed logins per client IP and email with a fixed one-minute window in Redis.
 * Counting only failures means a user who types the right password is never blocked
 * by their own successful logins; Redis keeps the counter shared across API instances.
 */
@Component
public class LoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final int maxFailuresPerWindow;

    public LoginRateLimiter(StringRedisTemplate redis,
                            @Value("${geops.rate-limit.login-per-minute:5}") int maxFailuresPerWindow) {
        this.redis = redis;
        this.maxFailuresPerWindow = maxFailuresPerWindow;
    }

    public void ensureAllowed(String clientIp, String email) {
        String key = key(clientIp, email);
        String failures = redis.opsForValue().get(key);
        if (failures != null && Integer.parseInt(failures) >= maxFailuresPerWindow) {
            Long ttl = redis.getExpire(key);
            long retryAfter = ttl == null || ttl <= 0 ? WINDOW.toSeconds() : ttl;
            throw new RateLimitExceededException(retryAfter);
        }
    }

    public void recordFailure(String clientIp, String email) {
        String key = key(clientIp, email);
        Long failures = redis.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redis.expire(key, WINDOW);
        }
    }

    public void reset(String clientIp, String email) {
        redis.delete(key(clientIp, email));
    }

    private static String key(String clientIp, String email) {
        return "rl:login:" + clientIp + ":" + email.strip().toLowerCase(Locale.ROOT);
    }
}
