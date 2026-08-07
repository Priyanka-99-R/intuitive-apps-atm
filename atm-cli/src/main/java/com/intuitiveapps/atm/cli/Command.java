package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.CustomerName;
import com.intuitiveapps.atm.domain.Money;

/**
 * A parsed, validated instruction.
 *
 * <p>Parsing is separated from execution so that "is this line well formed?" and "is this
 * operation allowed?" are answered in different places and can be tested independently. By the
 * time a {@code Command} exists, the text is known to be syntactically valid and the amounts and
 * names have already become domain types - the shell never handles a raw {@code String}.
 *
 * <p>Sealed, so the compiler can tell us if a new command is added and left unhandled.
 */
public sealed interface Command {

    record Login(CustomerName name) implements Command {
    }

    record Deposit(Money amount) implements Command {
    }

    record Withdraw(Money amount) implements Command {
    }

    record Transfer(CustomerName target, Money amount) implements Command {
    }

    record Logout() implements Command {
    }

    record Help() implements Command {
    }

    record Exit() implements Command {
    }
}
