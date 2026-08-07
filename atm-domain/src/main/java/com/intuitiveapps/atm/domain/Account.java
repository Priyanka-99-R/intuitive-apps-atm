package com.intuitiveapps.atm.domain;

/**
 * A customer's cash balance.
 *
 * <p>The mutators are package private on purpose. {@link Bank} is the aggregate root and the only
 * thing allowed to move money; if {@code credit} and {@code debit} were public, a caller could
 * increase one balance without decreasing another and the books would not balance. Keeping them
 * package private makes that a compile error rather than a code review comment.
 */
final class Account {

    private final CustomerName name;
    private Money balance;

    Account(CustomerName name) {
        this.name = name;
        this.balance = Money.ZERO;
    }

    CustomerName name() {
        return name;
    }

    Money balance() {
        return balance;
    }

    void credit(Money amount) {
        balance = balance.plus(amount);
    }

    /**
     * @throws com.intuitiveapps.atm.domain.exception.InvalidAmountException if the account does
     *         not hold {@code amount}. Callers check availability first; this is the invariant
     *         backstop that prevents a negative balance from ever existing.
     */
    void debit(Money amount) {
        balance = balance.minus(amount);
    }

    @Override
    public String toString() {
        return name + "=$" + balance;
    }
}
