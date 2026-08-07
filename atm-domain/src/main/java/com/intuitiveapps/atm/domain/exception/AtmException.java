package com.intuitiveapps.atm.domain.exception;

/**
 * Base type for every rule violation the domain can report.
 *
 * <p>These are unchecked. The distinction being drawn is between <em>expected, actionable</em>
 * failures - the customer typed a name that does not exist, or asked for more money than they
 * have - and genuine defects. Both are unchecked here, but every {@code AtmException} carries a
 * message that is safe and useful to show a customer, which is what lets each adapter translate
 * the whole family at a single point: one {@code catch} in the CLI shell, one
 * {@code @RestControllerAdvice} in the web module.
 *
 * <p>The alternative - checked exceptions - would force {@code throws} clauses through every
 * layer for conditions that no intermediate caller can do anything about, and in practice
 * encourages the empty {@code catch} block that hides them.
 */
public abstract class AtmException extends RuntimeException {

    protected AtmException(String message) {
        super(message);
    }
}
