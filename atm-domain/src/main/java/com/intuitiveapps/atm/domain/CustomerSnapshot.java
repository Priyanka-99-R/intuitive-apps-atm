package com.intuitiveapps.atm.domain;

import java.util.List;

/**
 * An immutable view of one customer's position at a point in time.
 *
 * <p>Returned instead of the live {@link Account} so that callers - the CLI, the REST layer,
 * tests - cannot reach into the aggregate and change a balance behind the {@link Bank}'s back.
 *
 * @param name        who this describes
 * @param balance     cash currently available to withdraw
 * @param obligations outstanding debts, those owed by this customer first, then those owed to
 *                    them; within each group, oldest first
 */
public record CustomerSnapshot(CustomerName name, Money balance, List<Obligation> obligations) {

    public CustomerSnapshot {
        obligations = List.copyOf(obligations);
    }

    public List<Obligation> debtsOwedByMe() {
        return obligations.stream().filter(Obligation::isOwedTo).toList();
    }

    public List<Obligation> debtsOwedToMe() {
        return obligations.stream().filter(o -> !o.isOwedTo()).toList();
    }
}
