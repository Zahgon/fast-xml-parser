// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.cli;

import io.github.naturalintelligence.fxp.js.Utf8;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * The {@code fast-xml-parser} command. It takes no arguments, reads JSON-lines requests on standard input (UTF-8, one
 * request per non-empty line) and writes one JSON response line per request, in order: {@code parse},
 * {@code validate}, {@code build} and {@code cli} operations over the library, or
 * {@code {"error":{"name":...,"message":...}}} when a request fails. The exit status is 0 when every request
 * succeeded, 1 when one failed, and 2 (with the usage text on standard error) for any argument.
 */
public final class Main {
    /** The usage text written for any command-line argument. */
    public static final String USAGE = "usage: fast-xml-parser < requests.jsonl\n"
            + "\n"
            + "Reads one JSON request per line on standard input and writes one JSON response per line.\n"
            + "Requests: {\"op\":\"parse\",\"xml\":X,...}, {\"op\":\"validate\",\"xml\":S}, {\"op\":\"build\",\"data\":D},\n"
            + "          {\"op\":\"cli\",\"args\":[...],\"input\":S}\n"
            + "with optional \"options\".\n";

    private Main() {
    }

    /**
     * Runs the command.
     *
     * @param args the command-line arguments; any is a usage error
     * @throws IOException when standard input cannot be read or the output cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            PrintStream err = new PrintStream(System.err, false, StandardCharsets.UTF_8);
            err.print(USAGE);
            err.flush();
            System.exit(2);
        }
        byte[] input = System.in.readAllBytes();
        Command.Output output = Command.run(Utf8.decode(input, input.length));
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.print(output.stdout());
        out.flush();
        if (out.checkError()) {
            throw new IOException("cannot write standard output");
        }
        System.exit(output.status());
    }
}
