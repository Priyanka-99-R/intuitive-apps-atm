package com.intuitiveapps.atm.cli;

import com.intuitiveapps.atm.domain.Bank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Entry point for the command line application.
 *
 * <p>The {@link Bank} is constructed here and nowhere else, and it is held only in memory. That
 * is what satisfies the "clean start on every invocation" requirement: there is no file, no
 * database and no cache, so stopping the process is the only reset the system has or needs.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        // System.console() is null when input is piped or redirected. In that case the terminal
        // is not echoing what was typed, so the shell does it instead and the transcript stays
        // readable.
        boolean interactive = System.console() != null;

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            Writer out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
            new AtmShell(new Bank(), in, out, !interactive).run();
        }
    }
}
