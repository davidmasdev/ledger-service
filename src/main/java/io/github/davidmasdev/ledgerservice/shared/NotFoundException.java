package io.github.davidmasdev.ledgerservice.shared;

/** The resource named in the URL does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
