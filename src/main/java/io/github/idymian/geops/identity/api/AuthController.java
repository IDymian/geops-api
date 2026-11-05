package io.github.idymian.geops.identity.api;

import io.github.idymian.geops.identity.application.dto.AuthDtos.AuthResponse;
import io.github.idymian.geops.identity.application.dto.AuthDtos.LoginRequest;
import io.github.idymian.geops.identity.application.dto.AuthDtos.RefreshRequest;
import io.github.idymian.geops.identity.application.dto.AuthDtos.RegisterRequest;
import io.github.idymian.geops.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sign up, sign in and session tokens")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a CONSUMER or PROVIDER account and sign in")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or PASSWORD_TOO_LONG")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    @ApiResponse(responseCode = "200", description = "Signed in")
    @ApiResponse(responseCode = "401", description = "AUTH_INVALID_CREDENTIALS")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED (see Retry-After header)")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair (the old one stops working)")
    @ApiResponse(responseCode = "200", description = "New token pair")
    @ApiResponse(responseCode = "401", description = "AUTH_REFRESH_INVALID")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the session of this refresh token on every device that shares it")
    @ApiResponse(responseCode = "204", description = "Signed out")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
