package io.github.davidmasdev.ledgerservice.shared;

import java.sql.SQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 Problem Details. The superclass already covers Spring MVC's own errors (bad JSON,
 * failed validation, missing headers) as 400s.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNIQUE_VIOLATION = "23505";

    @ExceptionHandler
    ProblemDetail handle(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(BusinessRuleViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    /**
     * A unique violation is a race the services' up-front checks cannot see, such as two concurrent reversals of the
     * same entry: a conflict. Any other integrity violation means invalid data got past validation, which is our bug.
     */
    @ExceptionHandler
    ProblemDetail handle(DataIntegrityViolationException e) {
        if (NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "The request conflicts with the current state of the ledger");
        }
        return serverError(e);
    }

    /** Anything unexpected still answers with a Problem Detail, without exposing internals. */
    @ExceptionHandler
    ProblemDetail handle(Exception e) {
        return serverError(e);
    }

    private ProblemDetail serverError(Exception e) {
        logger.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
