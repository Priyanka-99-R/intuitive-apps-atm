package com.intuitiveapps.atm.domain;

import java.util.List;

/**
 * What a command did, and where it left the customer.
 *
 * <p>Commands return this rather than {@code void} so that the presentation layer never has to
 * reconstruct what happened by diffing balances. Everything the sample session prints is
 * derivable from these two fields.
 *
 * @param cashTransfers cash that moved out of the acting customer's balance as a result of this
 *                      command, in the order it moved. Empty when nothing moved.
 * @param snapshot      the acting customer's position afterwards
 */
public record TransactionResult(List<CashTransfer> cashTransfers, CustomerSnapshot snapshot) {

    public TransactionResult {
        cashTransfers = List.copyOf(cashTransfers);
    }

    public static TransactionResult noCashMovement(CustomerSnapshot snapshot) {
        return new TransactionResult(List.of(), snapshot);
    }
}
