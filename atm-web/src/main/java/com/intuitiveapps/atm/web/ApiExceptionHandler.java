package com.intuitiveapps.atm.web;

import com.intuitiveapps.atm.domain.exception.AtmException;
import com.intuitiveapps.atm.domain.exception.CustomerNotFoundException;
import com.intuitiveapps.atm.domain.exception.InsufficientFundsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates domain failures into HTTP.
 *
 * <p>One place, for the whole family. This is the payoff for giving every rule violation a common
 * {@link AtmException} supertype: no controller needs a {@code try/catch}, and a new exception
 * type gets sensible behaviour for free rather than falling through as a 500.
 *
 * <p>Responses use {@link ProblemDetail} (RFC 9457) rather than a hand-rolled error shape, so
 * clients get a documented, predictable envelope.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {
    // The explicit ordering matters. With spring.mvc.problemdetails.enabled=true, Spring
    // registers its own advice for the exceptions it knows about - including
    // MethodArgumentNotValidException - and without a declared precedence it is unspecified
    // which advice wins. Highest precedence makes this one authoritative, so the wording of an
    // error is decided here rather than by whichever advice happened to be consulted first.

    /** The customer does not exist - genuinely "not found". */
    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail handleNotFound(CustomerNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Customer not found", e.getMessage());
    }

    /**
     * The request was well formed and the customer exists; the bank's state is what makes it
     * impossible. That is 409, not 400 - retrying after a deposit would succeed.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.CONFLICT, "Insufficient funds", e.getMessage());
    }

    /**
     * Everything else the domain refuses - a malformed amount, an invalid name, a transfer to
     * yourself - is a bad request. Listed after the more specific handlers, which Spring prefers
     * on exact type match.
     */
    @ExceptionHandler(AtmException.class)
    ProblemDetail handleDomainRule(AtmException e) {
        return problem(HttpStatus.BAD_REQUEST, "Request rejected", e.getMessage());
    }

    /** Bean-validation failures on the request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
