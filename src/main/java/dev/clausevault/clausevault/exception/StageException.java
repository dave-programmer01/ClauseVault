package dev.clausevault.clausevault.exception;

/** A pipeline stage could not complete: NEAR AI unreachable, truncated output, unparseable JSON. Mapped to 502. */
public class StageException extends RuntimeException {

    public StageException(String message) {
        super(message);
    }

    public StageException(String message, Throwable cause) {
        super(message, cause);
    }
}
