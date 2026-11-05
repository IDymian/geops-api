package io.github.idymian.geops;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base for integration tests: real PostGIS + Redis containers and one shared
 * Spring context (same configuration everywhere, so it is cached between classes).
 */
@Import({TestcontainersConfiguration.class, RoleProbeController.class})
@SpringBootTest(properties = {
    "geops.cors.allowed-origins=http://localhost:4200",
    // Test-only key: base64 of a fixed 48-byte string, never used outside tests.
    "geops.jwt.secret=Z2VvcHMtaW50ZWdyYXRpb24tdGVzdHMtb25seS1ub3QtYS1yZWFsLXNlY3JldCEh",
    "geops.rate-limit.login-per-minute=5"
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {
}
