package com.intuitiveapps.atm.domain.exception;

/** Raised when an amount is unparseable, negative, or zero where a positive amount is required. */
public class InvalidAmountException extends AtmException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
