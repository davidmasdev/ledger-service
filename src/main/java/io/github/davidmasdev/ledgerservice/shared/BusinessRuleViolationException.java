package io.github.davidmasdev.ledgerservice.shared;

/** The request is well-formed but breaks a ledger rule (e.g. an unbalanced entry). Mapped to 422. */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
