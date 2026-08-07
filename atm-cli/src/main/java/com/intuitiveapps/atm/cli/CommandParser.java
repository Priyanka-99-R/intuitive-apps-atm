package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.CustomerName;
import com.intuitiveapps.atm.domain.Money;

import java.util.Locale;
import java.util.Optional;

/**
 * Turns a line of text into a {@link Command}.
 *
 * <p>Pure and stateless: same input, same output, no side effects, nothing to mock. That is what
 * makes the parser exhaustively testable on its own, which matters because input handling is
 * where most of the odd cases live.
 *
 * <p><strong>Verbs are matched case insensitively</strong> ({@code LOGIN}, {@code Login} and
 * {@code login} are the same command) while <strong>customer names are not</strong>. The
 * asymmetry is deliberate: a verb is part of the interface and should be forgiving, whereas a
 * name is data and folding its case would silently merge two customers.
 */
public final class CommandParser {

    private CommandParser() {
    }

    /**
     * @return the parsed command, or empty if the line was blank or a {@code #} comment
     * @throws CommandException if the verb is unknown or the arguments do not fit it
     * @throws com.intuitiveapps.atm.domain.exception.AtmException if an argument is present but
     *         not a valid amount or customer name
     */
    public static Optional<Command> parse(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }

        String[] tokens = trimmed.split("\\s+");
        String verb = tokens[0].toLowerCase(Locale.ROOT);
        int arguments = tokens.length - 1;

        return Optional.of(switch (verb) {
            case "login" -> {
                requireArgumentCount(arguments, 1, "login [name]");
                yield new Command.Login(CustomerName.of(tokens[1]));
            }
            case "deposit" -> {
                requireArgumentCount(arguments, 1, "deposit [amount]");
                yield new Command.Deposit(Money.parse(tokens[1]));
            }
            case "withdraw" -> {
                requireArgumentCount(arguments, 1, "withdraw [amount]");
                yield new Command.Withdraw(Money.parse(tokens[1]));
            }
            case "transfer" -> {
                requireArgumentCount(arguments, 2, "transfer [target] [amount]");
                yield new Command.Transfer(CustomerName.of(tokens[1]), Money.parse(tokens[2]));
            }
            case "logout" -> {
                requireArgumentCount(arguments, 0, "logout");
                yield new Command.Logout();
            }
            case "help", "?" -> new Command.Help();
            case "exit", "quit" -> new Command.Exit();
            default -> throw new CommandException(
                    "Unknown command '" + tokens[0] + "'. Type 'help' to see the available commands.");
        });
    }

    private static void requireArgumentCount(int actual, int expected, String usage) {
        if (actual != expected) {
            throw new CommandException("Usage: " + usage);
        }
    }
}
