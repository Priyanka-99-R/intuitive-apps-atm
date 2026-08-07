package com.intuitiveapps.atm.domain.exception;

import com.intuitiveapps.atm.domain.Money;

/**
 * Raised when a withdrawal exceeds the available balance.
 *
 * <p>Note that this applies to {@code withdraw} only. A {@code transfer} that exceeds the
 * balance is <em>not</em> an error: the shortfall becomes an obligation, which is the behaviour
 * the specification requires.
 */
public class InsufficientFundsException extends AtmException {

    public InsufficientFundsException(Money requested, Money available) {
        super("Cannot withdraw $" + requested + " - your balance is $" + available);
    }
}
