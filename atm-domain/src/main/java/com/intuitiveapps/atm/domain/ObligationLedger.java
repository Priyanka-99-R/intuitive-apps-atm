package com.intuitiveapps.atm.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who owes what to whom.
 *
 * <p><strong>The central design decision in this problem.</strong> Obligations are held
 * <em>netted per pair</em>: at any moment at most one direction of a given pair is non-zero. If
 * Bob owes Alice $40 and Alice then transfers $30 to Bob, the ledger does not record two facing
 * debts - it records the single remaining fact that Bob owes Alice $10.
 *
 * <p>That is what makes the specification's sample session work. Alice's {@code transfer Bob 30}
 * leaves her balance untouched at $210 and prints no {@code Transferred} line, because no cash
 * needs to move: the transfer is satisfied entirely by cancelling part of what Bob already owed
 * her. Modelling the two debts separately would have moved $30 of real cash and then left Alice
 * owed $40 and owing $30, which contradicts the expected output.
 *
 * <p>Within a debtor, creditors are kept in the order the debts were first incurred, and that is
 * the order they are repaid in. Oldest-first is the least surprising rule and the easiest to
 * explain to a customer; any other policy (largest first, smallest first) would need a reason.
 *
 * <p>Not thread safe - see the README on concurrency.
 */
final class ObligationLedger {

    /** debtor -> creditor -> amount still owed. Insertion ordered, so repayment is FIFO. */
    private final Map<CustomerName, LinkedHashMap<CustomerName, Money>> owed = new LinkedHashMap<>();

    /**
     * Registers a new obligation from {@code debtor} to {@code creditor}, first cancelling
     * anything the creditor already owed the debtor.
     */
    void add(CustomerName debtor, CustomerName creditor, Money amount) {
        Money facing = amountOwed(creditor, debtor);
        Money remaining = amount;

        if (facing.isPositive()) {
            Money cancelled = Money.min(facing, remaining);
            reduce(creditor, debtor, cancelled);
            remaining = remaining.minus(cancelled);
        }
        if (remaining.isPositive()) {
            owed.computeIfAbsent(debtor, key -> new LinkedHashMap<>())
                    .merge(creditor, remaining, Money::plus);
        }
    }

    /** Repays part or all of an existing obligation. */
    void reduce(CustomerName debtor, CustomerName creditor, Money amount) {
        LinkedHashMap<CustomerName, Money> byCreditor = owed.get(debtor);
        if (byCreditor == null) {
            return;
        }
        Money current = byCreditor.get(creditor);
        if (current == null) {
            return;
        }
        Money remaining = current.minus(amount);
        if (remaining.isZero()) {
            byCreditor.remove(creditor);
            if (byCreditor.isEmpty()) {
                owed.remove(debtor);
            }
        } else {
            byCreditor.put(creditor, remaining);
        }
    }

    Money amountOwed(CustomerName debtor, CustomerName creditor) {
        return owed.getOrDefault(debtor, new LinkedHashMap<>()).getOrDefault(creditor, Money.ZERO);
    }

    /**
     * @return a defensive, insertion-ordered copy, so callers may repay while iterating
     */
    Map<CustomerName, Money> creditorsOf(CustomerName debtor) {
        LinkedHashMap<CustomerName, Money> byCreditor = owed.get(debtor);
        return byCreditor == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(byCreditor));
    }

    /** Total still owed by everyone to everyone. Used by tests as a system-wide invariant. */
    Money totalOutstanding() {
        Money total = Money.ZERO;
        for (Map<CustomerName, Money> byCreditor : owed.values()) {
            for (Money amount : byCreditor.values()) {
                total = total.plus(amount);
            }
        }
        return total;
    }

    /**
     * Every obligation touching {@code customer}, debts they owe first and then debts owed to
     * them, oldest first within each group.
     *
     * <p>The scan over all debtors to find money owed <em>to</em> this customer is O(customers).
     * At the scale of a single ATM that is irrelevant, and one honest map beats two maps that can
     * disagree with each other. A production ledger with millions of accounts would index both
     * directions - and would be a database, not a {@code HashMap}.
     */
    List<Obligation> obligationsOf(CustomerName customer) {
        List<Obligation> obligations = new ArrayList<>();

        creditorsOf(customer).forEach((creditor, amount) ->
                obligations.add(new Obligation(creditor, amount, Obligation.Direction.OWED_TO)));

        owed.forEach((debtor, byCreditor) -> {
            Money amount = byCreditor.get(customer);
            if (amount != null) {
                obligations.add(new Obligation(debtor, amount, Obligation.Direction.OWED_FROM));
            }
        });

        return obligations;
    }
}
