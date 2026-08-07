package com.intuitiveapps.atm.domain;

/**
 * An outstanding debt between two customers, seen from one customer's point of view.
 *
 * <p>The direction is an explicit enum rather than the sign of the amount. A negative
 * {@link Money} is unrepresentable by design, and "owed $40 to Bob" versus "owed $40 from Bob"
 * is exactly the kind of distinction that should be impossible to get wrong by dropping a minus
 * sign.
 *
 * @param counterparty the other party
 * @param amount       always positive
 * @param direction    whether the viewer owes the counterparty, or is owed by them
 */
public record Obligation(CustomerName counterparty, Money amount, Direction direction) {

    public enum Direction {
        /** The viewer owes the counterparty. */
        OWED_TO,
        /** The counterparty owes the viewer. */
        OWED_FROM
    }

    public boolean isOwedTo() {
        return direction == Direction.OWED_TO;
    }
}
