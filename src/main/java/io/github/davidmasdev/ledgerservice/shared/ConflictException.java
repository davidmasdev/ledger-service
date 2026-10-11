package io.github.davidmasdev.ledgerservice.shared;

/** The request conflicts with the current state (e.g. a duplicate name). Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
