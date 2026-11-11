package dev.clausevault.clausevault.exception;

import org.springframework.http.HttpStatus;

/** A problem with the input (bad file type, no text, too long): mapped to a 4xx. */
public class AuditException extends RuntimeException {

    private final HttpStatus status;

    public AuditException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
