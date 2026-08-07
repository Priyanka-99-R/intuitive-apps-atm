package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.CashTransfer;
import com.intuitiveapps.atm.domain.CustomerSnapshot;
import com.intuitiveapps.atm.domain.Obligation;
import com.intuitiveapps.atm.domain.TransactionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders domain results as the lines the specification asks for.
 *
 * <p>All wording and the {@code $} sign live here and nowhere else. The domain deals in
 * {@link com.intuitiveapps.atm.domain.Money}, not in dollars-with-a-symbol, so changing the
 * currency or translating the interface touches this class alone.
 *
 * <p>Returns lists of lines rather than writing to a stream, which is what lets the shell be
 * tested by asserting on strings instead of by capturing {@code System.out}.
 */
final class OutputFormatter {

    private OutputFormatter() {
    }

    /** {@code Hello, Alice!} followed by the customer's position. */
    static List<String> greeting(CustomerSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("Hello, " + snapshot.name() + "!");
        lines.addAll(position(snapshot));
        return lines;
    }

    static String farewell(com.intuitiveapps.atm.domain.CustomerName name) {
        return "Goodbye, " + name + "!";
    }

    /**
     * Any cash that moved, then the resulting position.
     *
     * <p>A {@code Transferred ...} line appears only when cash genuinely left the customer's
     * balance. A transfer that is fully absorbed by netting off an existing debt prints no such
     * line, which is exactly what the sample session shows for Alice's {@code transfer Bob 30}.
     */
    static List<String> transactionResult(TransactionResult result) {
        List<String> lines = new ArrayList<>();
        for (CashTransfer transfer : result.cashTransfers()) {
            lines.add("Transferred $" + transfer.amount() + " to " + transfer.to());
        }
        lines.addAll(position(result.snapshot()));
        return lines;
    }

    /** {@code Your balance is $210} plus one line per outstanding obligation. */
    static List<String> position(CustomerSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("Your balance is $" + snapshot.balance());
        for (Obligation obligation : snapshot.obligations()) {
            String direction = obligation.isOwedTo() ? "to" : "from";
            lines.add("Owed $" + obligation.amount() + " " + direction + " " + obligation.counterparty());
        }
        return lines;
    }

    static List<String> help() {
        return List.of(
                "Commands:",
                "  login [name]               log in, creating the customer if new",
                "  deposit [amount]           pay money in (settles any debts first)",
                "  withdraw [amount]          take money out",
                "  transfer [target] [amount] send money to another customer",
                "  logout                     end the current session",
                "  help                       show this list",
                "  exit                       quit the application");
    }
}
