package io.github.idymian.geops.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Business error with an HTTP status and a stable, machine-readable code
 * (for example {@code AUTH_INVALID_CREDENTIALS}) that clients can rely on.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
