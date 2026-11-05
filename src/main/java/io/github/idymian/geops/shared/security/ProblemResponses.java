package io.github.idymian.geops.shared.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 401 and 403 answers from the security filter chain, in the same
 * {@code application/problem+json} format as every other API error.
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED",
                    "A valid access token is required.");
        };
    }

    public static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action.");
    }

    // Static, fixed messages only: nothing from the request is echoed back.
    private static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail, code));
    }
}
