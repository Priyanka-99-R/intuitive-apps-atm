package com.intuitiveapps.atm.domain.exception;

import com.intuitiveapps.atm.domain.CustomerName;

/**
 * Raised when an operation names a customer who has never logged in.
 *
 * <p>Only {@code login} creates accounts. A transfer to an unknown name is refused rather than
 * silently opening an account for the target - see the "Design decisions" section of the README.
 */
public class CustomerNotFoundException extends AtmException {

    public CustomerNotFoundException(CustomerName name) {
        super("No such customer: " + name.value());
    }
}
