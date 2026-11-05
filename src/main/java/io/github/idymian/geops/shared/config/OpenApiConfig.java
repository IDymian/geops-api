package io.github.idymian.geops.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata shown in Swagger UI ({@code /swagger-ui.html}).
 * Protected endpoints declare {@code @SecurityRequirement(name = "bearerAuth")}.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "GeoPS API",
        version = "v1",
        description = "Hyperlocal offers and coupons that connect local businesses with nearby consumers.",
        license = @License(name = "MIT", url = "https://opensource.org/licenses/MIT")))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
