package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.CustomerName;

/**
 * Who is currently standing at the terminal.
 *
 * <p>Session state lives in the adapter, not in the {@link com.intuitiveapps.atm.domain.Bank}.
 * A bank has customers; a <em>terminal</em> has one customer at a time. Putting "who is logged
 * in" into the domain would mean the REST adapter inherited a single global logged-in user, which
 * is obviously wrong the moment two browsers connect.
 */
final class Session {

    private CustomerName current;

    boolean isLoggedIn() {
        return current != null;
    }

    CustomerName currentCustomer() {
        if (current == null) {
            throw new CommandException("You are not logged in. Use 'login [name]' first.");
        }
        return current;
    }

    void logIn(CustomerName name) {
        if (current != null) {
            throw new CommandException(
                    "Already logged in as " + current + ". Use 'logout' first.");
        }
        current = name;
    }

    /**
     * Ends the session.
     *
     * @return the customer who was logged in
     * @throws CommandException if nobody was
     */
    CustomerName logOut() {
        CustomerName who = currentCustomer();
        current = null;
        return who;
    }
}
