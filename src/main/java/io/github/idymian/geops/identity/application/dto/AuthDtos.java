package io.github.idymian.geops.identity.application.dto;

import java.util.UUID;

import io.github.idymian.geops.identity.domain.Role;
import io.github.idymian.geops.identity.domain.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies of the auth endpoints. Entities never leave the API layer.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254)
            @Schema(example = "ana@example.com")
            String email,

            @NotBlank
            @Size(min = 8, max = 64, message = "must be between 8 and 64 characters")
            @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "must contain at least one letter and one number")
            @Schema(example = "Secreta123")
            String password,

            @NotBlank @Size(max = 120)
            @Schema(example = "Ana Torres")
            String fullName,

            @NotNull
            @Schema(example = "CONSUMER")
            Role role) {
    }

    public record LoginRequest(
            @NotBlank @Email @Schema(example = "ana@example.com") String email,
            @NotBlank @Schema(example = "Secreta123") String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record UserResponse(UUID id, String email, String fullName, Role role) {

        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        }
    }

    public record AuthResponse(
            @Schema(example = "Bearer") String tokenType,
            String accessToken,
            @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
            String refreshToken,
            UserResponse user) {
    }
}
