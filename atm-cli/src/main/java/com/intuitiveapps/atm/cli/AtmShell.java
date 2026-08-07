package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.Bank;
import com.intuitiveapps.atm.domain.CustomerSnapshot;
import com.intuitiveapps.atm.domain.TransactionResult;
import com.intuitiveapps.atm.domain.exception.AtmException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

/**
 * The read-evaluate-print loop.
 *
 * <p>Takes a {@link Reader} and a {@link Writer} rather than reaching for {@code System.in} and
 * {@code System.out}, so the whole shell can be driven from a string in a test and its output
 * asserted exactly. That is the difference between testing the application and testing a
 * fragment of it.
 */
public final class AtmShell {

    private static final String PROMPT = "$ ";

    private final Bank bank;
    private final BufferedReader in;
    private final PrintWriter out;
    private final boolean echoInput;
    private final Session session = new Session();

    /**
     * @param echoInput when true, each line read is echoed after the prompt. Set when input is
     *                  piped rather than typed, so that a redirected session still reads as a
     *                  conversation instead of a wall of unattributed output.
     */
    public AtmShell(Bank bank, Reader in, Writer out, boolean echoInput) {
        this.bank = bank;
        this.in = new BufferedReader(in);
        this.out = new PrintWriter(out, true);
        this.echoInput = echoInput;
    }

    public void run() {
        printBanner();
        try {
            loop();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read from the terminal", e);
        } finally {
            out.flush();
        }
    }

    private void loop() throws IOException {
        while (true) {
            out.print(PROMPT);
            out.flush();

            String line = in.readLine();
            if (line == null) {
                // End of input: Ctrl-D at a terminal, or the end of a piped script.
                out.println();
                closeSessionQuietly();
                return;
            }
            if (echoInput) {
                out.println(line);
            }

            try {
                Optional<Command> command = CommandParser.parse(line);
                if (command.isEmpty()) {
                    continue;   // blank line or comment - no prompt-sized gap for nothing
                }
                if (!execute(command.get())) {
                    return;
                }
            } catch (CommandException | AtmException e) {
                // Every expected failure lands here. The loop continues: a mistyped command is
                // part of using an ATM, not a reason to end the session. Unexpected exceptions
                // are deliberately not caught - a defect should surface, not be swallowed.
                out.println("Error: " + e.getMessage());
            }
            out.println();
        }
    }

    /**
     * @return false if the application should stop
     */
    private boolean execute(Command command) {
        // An if/else chain rather than a pattern switch because this targets Java 17, where
        // switch patterns are still a preview feature. Command is sealed, so the set of cases is
        // closed and adding one without handling it here is caught by the parser's tests.
        if (command instanceof Command.Login login) {
            session.logIn(login.name());
            CustomerSnapshot snapshot = bank.login(login.name());
            print(OutputFormatter.greeting(snapshot));
        } else if (command instanceof Command.Deposit deposit) {
            TransactionResult result = bank.deposit(session.currentCustomer(), deposit.amount());
            print(OutputFormatter.transactionResult(result));
        } else if (command instanceof Command.Withdraw withdraw) {
            TransactionResult result = bank.withdraw(session.currentCustomer(), withdraw.amount());
            print(OutputFormatter.transactionResult(result));
        } else if (command instanceof Command.Transfer transfer) {
            TransactionResult result =
                    bank.transfer(session.currentCustomer(), transfer.target(), transfer.amount());
            print(OutputFormatter.transactionResult(result));
        } else if (command instanceof Command.Logout) {
            out.println(OutputFormatter.farewell(session.logOut()));
        } else if (command instanceof Command.Help) {
            print(OutputFormatter.help());
        } else if (command instanceof Command.Exit) {
            closeSessionQuietly();
            return false;
        }
        return true;
    }

    /**
     * Ends an open session on the way out.
     *
     * <p>Quitting while logged in is treated as an implicit logout rather than an error - walking
     * away from a terminal is a normal thing to do, and the alternative is refusing to let
     * somebody quit.
     */
    private void closeSessionQuietly() {
        if (session.isLoggedIn()) {
            out.println(OutputFormatter.farewell(session.logOut()));
        }
    }

    private void printBanner() {
        out.println("ATM ready. Type 'help' for commands, 'exit' to quit.");
        out.println("This session starts empty - no customers exist until somebody logs in.");
        out.println();
    }

    private void print(List<String> lines) {
        lines.forEach(out::println);
    }
}
