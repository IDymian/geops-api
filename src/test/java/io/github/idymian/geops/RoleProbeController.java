package io.github.idymian.geops;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that check role-based access works end to end
 * (JWT role claim -> ROLE_* authority -> @PreAuthorize).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(RoleProbeController.Endpoints.class)
public class RoleProbeController {

    @RestController
    static class Endpoints {

        @GetMapping("/api/v1/test/provider-only")
        @PreAuthorize("hasRole('PROVIDER')")
        String providerOnly() {
            return "provider";
        }

        @GetMapping("/api/v1/test/consumer-only")
        @PreAuthorize("hasRole('CONSUMER')")
        String consumerOnly() {
            return "consumer";
        }
    }
}
