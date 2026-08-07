package com.intuitiveapps.atm.domain.exception;

/** Raised when a customer name is blank or contains characters the system does not accept. */
public class InvalidCustomerNameException extends AtmException {

    public InvalidCustomerNameException(String message) {
        super(message);
    }
}
