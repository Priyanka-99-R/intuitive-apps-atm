package com.intuitiveapps.atm.domain;

/**
 * A movement of actual cash from one balance to another, reported back to the caller so it can
 * be shown to the customer.
 *
 * <p>This exists because "transfer $100" and "$30 of cash moved" are different facts. A transfer
 * of $100 against a $30 balance moves $30 now and leaves $70 as an {@link Obligation}; a transfer
 * that is entirely absorbed by netting off an existing debt moves nothing at all. The specification's
 * sample session prints a {@code Transferred ...} line only when cash genuinely moved, so the
 * domain reports cash movements rather than leaving the adapter to infer them.
 *
 * @param to     the customer whose balance increased
 * @param amount always positive
 */
public record CashTransfer(CustomerName to, Money amount) {
}
