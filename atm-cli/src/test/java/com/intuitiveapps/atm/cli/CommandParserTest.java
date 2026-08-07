package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.CustomerName;
import com.intuitiveapps.atm.domain.Money;
import com.intuitiveapps.atm.domain.exception.InvalidAmountException;
import com.intuitiveapps.atm.domain.exception.InvalidCustomerNameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CommandParserTest {

    @Test
    void parsesEveryCommand() {
        assertThat(CommandParser.parse("login Alice"))
                .contains(new Command.Login(CustomerName.of("Alice")));
        assertThat(CommandParser.parse("deposit 100"))
                .contains(new Command.Deposit(Money.of(100)));
        assertThat(CommandParser.parse("withdraw 24.50"))
                .contains(new Command.Withdraw(Money.parse("24.50")));
        assertThat(CommandParser.parse("transfer Bob 30"))
                .contains(new Command.Transfer(CustomerName.of("Bob"), Money.of(30)));
        assertThat(CommandParser.parse("logout")).contains(new Command.Logout());
        assertThat(CommandParser.parse("help")).contains(new Command.Help());
        assertThat(CommandParser.parse("exit")).contains(new Command.Exit());
        assertThat(CommandParser.parse("quit")).contains(new Command.Exit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOGIN Alice", "Login Alice", "lOgIn Alice"})
    @DisplayName("verbs are case insensitive")
    void acceptsAnyCaseOfVerb(String line) {
        assertThat(CommandParser.parse(line)).contains(new Command.Login(CustomerName.of("Alice")));
    }

    @Test
    @DisplayName("customer names are case sensitive, unlike verbs")
    void preservesNameCase() {
        assertThat(CommandParser.parse("login alice"))
                .contains(new Command.Login(CustomerName.of("alice")))
                .isNotEqualTo(CommandParser.parse("login Alice"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "# comment", "  # indented comment"})
    @DisplayName("blank lines and comments parse to nothing rather than to an error")
    void ignoresNoise(String line) {
        assertThat(CommandParser.parse(line)).isEmpty();
    }

    @Test
    void handlesUntidyWhitespace() {
        assertThat(CommandParser.parse("   transfer    Bob     30   "))
                .contains(new Command.Transfer(CustomerName.of("Bob"), Money.of(30)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "login a b", "deposit", "deposit 1 2", "transfer Bob",
            "transfer Bob 1 2", "logout now"})
    @DisplayName("the wrong number of arguments produces a usage message")
    void rejectsWrongArity(String line) {
        assertThatExceptionOfType(CommandException.class)
                .isThrownBy(() -> CommandParser.parse(line))
                .withMessageStartingWith("Usage:");
    }

    @Test
    void rejectsUnknownVerbs() {
        assertThatExceptionOfType(CommandException.class)
                .isThrownBy(() -> CommandParser.parse("withdrawal 10"))
                .withMessageContaining("Unknown command 'withdrawal'");
    }

    @Test
    @DisplayName("argument validation is delegated to the domain types, not duplicated here")
    void delegatesValueValidation() {
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> CommandParser.parse("deposit twelve"));
        assertThatExceptionOfType(InvalidCustomerNameException.class)
                .isThrownBy(() -> CommandParser.parse("login @lice"));
    }

    @Test
    void parsesNullAsNothing() {
        assertThat(CommandParser.parse(null)).isEmpty();
    }
}
