package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.Bank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the whole application through its real entry point and asserts on what a user would
 * actually see.
 *
 * <p>The first test is the important one: it feeds in the exact command sequence from the problem
 * statement and compares the complete transcript, character for character. Unit tests on the
 * {@code Bank} prove the arithmetic; this proves the product.
 */
class AtmShellTest {

    private List<String> run(String input) {
        StringWriter output = new StringWriter();
        new AtmShell(new Bank(), new StringReader(input), output, true).run();
        return output.toString().lines().toList();
    }

    @Test
    @DisplayName("the sample session produces exactly the transcript in the problem statement")
    void reproducesTheSampleTranscript() {
        List<String> transcript = run("""
                login Alice
                deposit 100
                logout
                login Bob
                deposit 80
                transfer Alice 50
                transfer Alice 100
                deposit 30
                logout
                login Alice
                transfer Bob 30
                logout
                login Bob
                deposit 100
                logout
                """);

        assertThat(transcript).containsExactly(
                "ATM ready. Type 'help' for commands, 'exit' to quit.",
                "This session starts empty - no customers exist until somebody logs in.",
                "",
                "$ login Alice",
                "Hello, Alice!",
                "Your balance is $0",
                "",
                "$ deposit 100",
                "Your balance is $100",
                "",
                "$ logout",
                "Goodbye, Alice!",
                "",
                "$ login Bob",
                "Hello, Bob!",
                "Your balance is $0",
                "",
                "$ deposit 80",
                "Your balance is $80",
                "",
                "$ transfer Alice 50",
                "Transferred $50 to Alice",
                "Your balance is $30",
                "",
                "$ transfer Alice 100",
                "Transferred $30 to Alice",
                "Your balance is $0",
                "Owed $70 to Alice",
                "",
                "$ deposit 30",
                "Transferred $30 to Alice",
                "Your balance is $0",
                "Owed $40 to Alice",
                "",
                "$ logout",
                "Goodbye, Bob!",
                "",
                "$ login Alice",
                "Hello, Alice!",
                "Your balance is $210",
                "Owed $40 from Bob",
                "",
                "$ transfer Bob 30",
                "Your balance is $210",
                "Owed $10 from Bob",
                "",
                "$ logout",
                "Goodbye, Alice!",
                "",
                "$ login Bob",
                "Hello, Bob!",
                "Your balance is $0",
                "Owed $10 to Alice",
                "",
                "$ deposit 100",
                "Transferred $10 to Alice",
                "Your balance is $90",
                "",
                "$ logout",
                "Goodbye, Bob!",
                "",
                "$ ");
    }

    @Test
    @DisplayName("commands before logging in are refused without ending the session")
    void refusesCommandsWhenLoggedOut() {
        assertThat(run("deposit 10\nwithdraw 10\ntransfer Bob 10\nlogout\nlogin Alice\n"))
                .filteredOn(line -> line.startsWith("Error:"))
                .hasSize(4)
                .allMatch(line -> line.contains("not logged in"));
    }

    @Test
    @DisplayName("logging in twice is refused rather than silently switching customer")
    void refusesDoubleLogin() {
        assertThat(run("login Alice\nlogin Bob\n"))
                .contains("Error: Already logged in as Alice. Use 'logout' first.");
    }

    @Test
    @DisplayName("a bad command is reported and the session carries on")
    void recoversFromBadInput() {
        List<String> transcript = run("""
                login Alice
                deposit
                deposit abc
                deposit -5
                withdraw 10
                frobnicate
                transfer Nobody 5
                transfer Alice 5
                deposit 10
                """);

        assertThat(transcript).contains(
                "Error: Usage: deposit [amount]",
                "Error: 'abc' is not a valid amount. Expected a number such as 100 or 24.50",
                "Error: '-5' is not a valid amount. Expected a number such as 100 or 24.50",
                "Error: Cannot withdraw $10 - your balance is $0",
                "Error: Unknown command 'frobnicate'. Type 'help' to see the available commands.",
                "Error: No such customer: Nobody",
                "Error: Cannot transfer to yourself, Alice");

        // ...and the session survived all of it: the last command still worked, and reaching the
        // end of input logged Alice out cleanly rather than dropping her mid-session.
        assertThat(transcript).contains("Your balance is $10");
        assertThat(transcript).endsWith("Goodbye, Alice!");
    }

    @Test
    @DisplayName("each run starts with no customers, as the brief requires")
    void startsClean() {
        run("login Alice\ndeposit 500\n");

        assertThat(run("login Alice\n")).contains("Your balance is $0");
    }

    @Test
    @DisplayName("exit while logged in says goodbye rather than refusing to quit")
    void exitLogsOutImplicitly() {
        assertThat(run("login Alice\nexit\n")).contains("Goodbye, Alice!");
    }

    @Test
    @DisplayName("blank lines and comments are ignored")
    void ignoresBlankLinesAndComments() {
        assertThat(run("\n   \n# a comment\nlogin Alice\n"))
                .containsSequence("$ login Alice", "Hello, Alice!");
    }

    @Test
    void helpListsEveryCommand() {
        assertThat(run("help\n"))
                .anyMatch(line -> line.contains("transfer [target] [amount]"))
                .anyMatch(line -> line.contains("withdraw [amount]"));
    }
}
