package com.intuitiveapps.atm.domain;

import com.intuitiveapps.atm.domain.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A non-negative monetary amount.
 *
 * <p>Money is modelled as an immutable value object over {@link BigDecimal} rather than as a
 * {@code double} or a {@code long} of cents. {@code double} is disqualified outright - binary
 * floating point cannot represent {@code 0.10} exactly, so balances drift. A {@code long} of
 * cents would be correct, but it pushes the scaling rules out to every call site, and the first
 * time somebody forgets to divide by 100 the bug is silent.
 *
 * <p>Amounts are held at a fixed scale of 2 and are constrained to be non-negative. Negative
 * money is not a thing this domain has any use for: a withdrawal is expressed as an operation,
 * not as a negative deposit, and the direction of an obligation is carried by
 * {@link Obligation.Direction} rather than by a sign. Making it unrepresentable removes a whole
 * category of defect.
 */
public final class Money implements Comparable<Money> {

    private static final int SCALE = 2;

    /**
     * Accepted input syntax. Deliberately stricter than {@link BigDecimal#BigDecimal(String)},
     * which would happily accept {@code 1e9}, {@code +5} and {@code -5}. An ATM keypad produces
     * plain decimals, so that is all we take.
     */
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("\\d+(\\.\\d{1,2})?");

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(long units) {
        if (units < 0) {
            throw new InvalidAmountException("Amount cannot be negative");
        }
        return new Money(BigDecimal.valueOf(units));
    }

    /**
     * Parses user input.
     *
     * @throws InvalidAmountException if the text is not a plain non-negative decimal with at
     *                                most two fractional digits. Rejecting rather than rounding
     *                                is intentional - silently turning a request for
     *                                {@code 10.999} into {@code 11.00} is the kind of helpfulness
     *                                that loses money.
     */
    public static Money parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidAmountException("Amount is required");
        }
        String trimmed = text.trim();
        if (!PLAIN_DECIMAL.matcher(trimmed).matches()) {
            throw new InvalidAmountException(
                    "'" + trimmed + "' is not a valid amount. Expected a number such as 100 or 24.50");
        }
        return new Money(new BigDecimal(trimmed));
    }

    public Money plus(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /**
     * @throws InvalidAmountException if the result would be negative. Callers in this domain
     *                                always check first; this is the backstop that turns a
     *                                logic error into a loud failure instead of a wrong balance.
     */
    public Money minus(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new InvalidAmountException("Operation would produce a negative amount");
        }
        return new Money(result);
    }

    public static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Scale is fixed at construction, so equals() and compareTo() agree - the classic
        // BigDecimal trap (2.0 vs 2.00) cannot arise here.
        return o instanceof Money other && amount.equals(other.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    /**
     * Renders the bare number: {@code 0}, {@code 100}, {@code 0.10}, {@code 24.50}.
     *
     * <p>Whole amounts drop the decimals, because the sample session shows {@code $0} and
     * {@code $100} rather than {@code $0.00}. Fractional amounts keep both places, because
     * {@code $0.1} is not how money is written. Simply stripping trailing zeros would satisfy the
     * first rule and break the second.
     *
     * <p>The currency symbol is deliberately not included - that is a presentation concern and
     * belongs to the adapter doing the presenting.
     */
    @Override
    public String toString() {
        return amount.stripTrailingZeros().scale() <= 0
                ? amount.toBigInteger().toString()
                : amount.toPlainString();
    }
}
