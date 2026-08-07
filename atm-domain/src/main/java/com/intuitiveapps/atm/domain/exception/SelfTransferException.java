package com.intuitiveapps.atm.domain.exception;

import com.intuitiveapps.atm.domain.CustomerName;

/**
 * Raised when a customer transfers to themselves.
 *
 * <p>Arguably a no-op rather than an error, since it cannot change any balance. It is rejected
 * because it is far more likely to be a typo than an intention, and an ATM that silently accepts
 * a mistyped transfer teaches the customer that the confirmation means nothing.
 */
public class SelfTransferException extends AtmException {

    public SelfTransferException(CustomerName name) {
        super("Cannot transfer to yourself, " + name.value());
    }
}
