package com.intuitiveapps.atm.domain;

import com.intuitiveapps.atm.domain.exception.InvalidCustomerNameException;

import java.util.regex.Pattern;

/**
 * The identity of a customer.
 *
 * <p>A wrapper around a {@code String} rather than a bare {@code String} so that a customer name
 * cannot be passed where an amount, a command or an arbitrary label is expected. It also gives
 * validation a single home.
 *
 * <p><strong>Names are case sensitive.</strong> {@code Alice} and {@code alice} are two different
 * customers. Case folding sounds friendlier but it is a decision about identity, and getting it
 * wrong in the lenient direction merges two people's money - so this implementation treats the
 * name as an opaque identifier and leaves case folding to a real identity system.
 *
 * @param value the validated name
 */
public record CustomerName(String value) implements Comparable<CustomerName> {

    private static final int MAX_LENGTH = 40;

    /**
     * Letters, digits, underscore, hyphen and apostrophe. Whitespace is excluded because the CLI
     * tokenises on whitespace: allowing {@code login Mary Jane} would make {@code transfer Mary
     * Jane 50} ambiguous to parse.
     */
    private static final Pattern VALID = Pattern.compile("[\\p{L}\\p{N}_'-]{1,%d}".formatted(MAX_LENGTH));

    public CustomerName {
        if (value == null || value.isBlank()) {
            throw new InvalidCustomerNameException("Customer name is required");
        }
        if (!VALID.matcher(value).matches()) {
            throw new InvalidCustomerNameException(
                    "'" + value + "' is not a valid customer name. Use up to " + MAX_LENGTH
                            + " letters, digits, underscores, hyphens or apostrophes, with no spaces");
        }
    }

    public static CustomerName of(String value) {
        return new CustomerName(value);
    }

    @Override
    public int compareTo(CustomerName other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
