package com.intuitiveapps.atm.cli;

/**
 * A problem with the interaction rather than with the banking: an unrecognised verb, the wrong
 * number of arguments, or an operation attempted before logging in.
 *
 * <p>Kept separate from
 * {@link com.intuitiveapps.atm.domain.exception.AtmException} because these conditions do not
 * exist in the domain. "You are not logged in" is meaningless to a bank - a {@code Bank} is asked
 * to move money between named customers and has no concept of a session. Session state belongs to
 * whatever is holding the conversation, which here is the terminal.
 */
public class CommandException extends RuntimeException {

    public CommandException(String message) {
        super(message);
    }
}
