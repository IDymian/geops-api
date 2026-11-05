package io.github.idymian.geops.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import io.github.idymian.geops.AbstractIntegrationTest;
import io.github.idymian.geops.identity.infrastructure.OpaqueTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AuthIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "Secreta123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // --- register ---

    @Test
    void registerCreatesAccountAndReturnsTokens() throws Exception {
        String email = uniqueEmail();

        register(email, "PROVIDER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("PROVIDER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void emailIsCaseInsensitiveAndMustBeUnique() throws Exception {
        String email = uniqueEmail();
        register(email, "CONSUMER").andExpect(status().isCreated());

        register(email.toUpperCase(), "CONSUMER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short", "fullName": "", "role": "CONSUMER"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void cannotRegisterWithUnknownRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "fullName": "Eve", "role": "ADMIN"}
                        """.formatted(uniqueEmail(), PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // --- login ---

    @Test
    void loginWithValidCredentials() throws Exception {
        String email = uniqueEmail();
        register(email, "CONSUMER");

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CONSUMER"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = uniqueEmail();
        register(email, "CONSUMER");

        String wrongPassword = login(email, "Wrong12345").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void tooManyFailedLoginsAreRateLimited() throws Exception {
        String email = uniqueEmail();
        register(email, "CONSUMER");

        for (int i = 0; i < 5; i++) {
            login(email, "Wrong12345").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    // --- access token and roles ---

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        String email = uniqueEmail();
        String accessToken = field(register(email, "PROVIDER"), "$.accessToken");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PROVIDER"));
    }

    @Test
    void rolesRestrictAccess() throws Exception {
        String provider = field(register(uniqueEmail(), "PROVIDER"), "$.accessToken");
        String consumer = field(register(uniqueEmail(), "CONSUMER"), "$.accessToken");

        mockMvc.perform(get("/api/v1/test/provider-only").header("Authorization", "Bearer " + provider))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test/provider-only").header("Authorization", "Bearer " + consumer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/test/consumer-only").header("Authorization", "Bearer " + consumer))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRejectMissingOrTamperedTokens() throws Exception {
        String accessToken = field(register(uniqueEmail(), "CONSUMER"), "$.accessToken");
        String tampered = accessToken.substring(0, accessToken.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    // --- refresh tokens ---

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        String refreshToken = field(register(uniqueEmail(), "CONSUMER"), "$.refreshToken");

        Integer plain = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, refreshToken);
        Integer hashed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, OpaqueTokens.sha256(refreshToken));
        assertThat(plain).isZero();
        assertThat(hashed).isOne();
    }

    @Test
    void refreshRotatesTokens() throws Exception {
        String first = field(register(uniqueEmail(), "CONSUMER"), "$.refreshToken");

        refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken", not(first)));
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        String first = field(register(uniqueEmail(), "CONSUMER"), "$.refreshToken");
        String second = field(refresh(first), "$.refreshToken");

        // An attacker replays the old token: rejected...
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        // ...and the legitimate newer token is revoked too.
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = field(register(uniqueEmail(), "CONSUMER"), "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("not-a-real-token")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("not-a-real-token"))));
    }

    // --- helpers ---

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions register(String email, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "fullName": "Test User", "role": "%s"}
                """.formatted(email, PASSWORD, role)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
    }

    private static String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
