package io.github.idymian.geops.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import io.github.idymian.geops.identity.application.dto.AuthDtos.AuthResponse;
import io.github.idymian.geops.identity.application.dto.AuthDtos.LoginRequest;
import io.github.idymian.geops.identity.application.dto.AuthDtos.RegisterRequest;
import io.github.idymian.geops.identity.application.dto.AuthDtos.UserResponse;
import io.github.idymian.geops.identity.domain.RefreshToken;
import io.github.idymian.geops.identity.domain.UserAccount;
import io.github.idymian.geops.identity.infrastructure.AccessTokenIssuer;
import io.github.idymian.geops.identity.infrastructure.JwtProperties;
import io.github.idymian.geops.identity.infrastructure.LoginRateLimiter;
import io.github.idymian.geops.identity.infrastructure.OpaqueTokens;
import io.github.idymian.geops.identity.infrastructure.RefreshTokenRepository;
import io.github.idymian.geops.identity.infrastructure.UserAccountRepository;
import io.github.idymian.geops.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh-token rotation and logout.
 */
@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes of a password. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter rateLimiter;
    private final Clock clock;

    /** Compared against when the email does not exist, so both paths take the same time. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       AccessTokenIssuer accessTokenIssuer,
                       JwtProperties jwtProperties,
                       LoginRateLimiter rateLimiter,
                       Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.jwtProperties = jwtProperties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "The password is too long.");
        }
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "An account with this email already exists.");
        }
        UserAccount user = users.save(new UserAccount(
                email, passwordEncoder.encode(request.password()), request.fullName(), request.role()));
        return issueTokens(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = UserAccount.normalizeEmail(request.email());
        rateLimiter.ensureAllowed(clientIp, email);

        UserAccount user = users.findByEmail(email).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyHash);

        if (user == null || !passwordMatches || !user.isEnabled()) {
            rateLimiter.recordFailure(clientIp, email);
            // Same answer whether the email exists or not: no account enumeration.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid email or password.");
        }
        rateLimiter.reset(clientIp, email);
        return issueTokens(user, UUID.randomUUID());
    }

    /**
     * Rotates a refresh token. Presenting a token that was already rotated means it
     * was copied by someone else, so the whole session family is revoked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(OpaqueTokens.sha256(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);

        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        if (!current.isActive(now) || !current.getUser().isEnabled()) {
            throw invalidRefreshToken();
        }
        current.revoke(now);
        return issueTokens(current.getUser(), current.getFamilyId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(OpaqueTokens.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .filter(UserAccount::isEnabled)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED",
                        "Your session is no longer valid."));
    }

    private AuthResponse issueTokens(UserAccount user, UUID familyId) {
        Instant now = clock.instant();
        String rawRefreshToken = OpaqueTokens.generate();
        refreshTokens.save(new RefreshToken(
                user, OpaqueTokens.sha256(rawRefreshToken), familyId, now.plus(jwtProperties.refreshTtl())));

        return new AuthResponse(
                "Bearer",
                accessTokenIssuer.issue(user, now),
                accessTokenIssuer.accessTtlSeconds(),
                rawRefreshToken,
                UserResponse.from(user));
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_INVALID",
                "Your session has expired. Please sign in again.");
    }
}
