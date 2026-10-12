package io.github.davidmasdev.ledgerservice.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void uniqueViolationIsAConflict() {
        var e = new DataIntegrityViolationException("dup", new SQLException("duplicate key", "23505"));

        assertThat(handler.handle(e).getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void otherIntegrityViolationsAreServerErrors() {
        var e = new DataIntegrityViolationException("fk", new SQLException("foreign key violation", "23503"));

        var problem = handler.handle(e);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("foreign key");
    }

    @Test
    void unexpectedExceptionsAreServerErrorProblems() {
        var problem = handler.handle(new IllegalStateException("secret internals"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("secret");
    }
}
