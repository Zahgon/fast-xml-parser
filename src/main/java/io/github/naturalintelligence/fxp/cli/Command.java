// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.cli;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.Json;

/**
 * The request loop of the fast-xml-parser command: every non-empty line of the input (split at "\n") is one JSON
 * request, answered by one JSON response line.
 */
public final class Command {
    private static final String METADATA_KEY = "@@xmlMetadata";

    private Command() {
    }

    /** The command's standard output and exit status for one input. */
    public record Output(String stdout, int status) {
    }

    /** One response line and whether it reports an error. */
    public record Answer(String line, boolean failed) {
    }

    /**
     * Answers every request of an input.
     *
     * @param input the decoded standard input
     * @return the response lines, each followed by "\n", and 0 when all succeeded, else 1
     */
    public static Output run(String input) {
        StringBuilder stdout = new StringBuilder();
        boolean failed = false;
        for (String line : input.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            Answer answer = answer(line);
            failed |= answer.failed();
            stdout.append(answer.line()).append('\n');
        }
        return new Output(stdout.toString(), failed ? 1 : 0);
    }

    /**
     * Answers one request line.
     *
     * @param line the request, JSON text
     * @return the response line
     */
    public static Answer answer(String line) {
        try {
            JsObject response = RequestHandler.handle(Json.parse(line));
            return new Answer(Json.stringify(response, "", Command::withMetadata), false);
        } catch (JsError error) {
            return new Answer(errorLine(error.name(), error.getMessage()), true);
        } catch (StackOverflowError error) {
            return new Answer(errorLine("RangeError", "Maximum call stack size exceeded"), true);
        } catch (RuntimeException error) {
            // No input may crash the command: anything else that escapes is still reported as this request's error.
            return new Answer(errorLine("Error", String.valueOf(error.getMessage())), true);
        }
    }

    private static String errorLine(String name, String message) {
        JsObject error = new JsObject();
        error.set("name", name);
        error.set("message", message);
        JsObject response = new JsObject();
        response.set("error", error);
        return Json.stringify(response);
    }

    // The metadata symbol of a parse result object, written as one more member.
    private static Object withMetadata(Object value) {
        if (value instanceof JsObject object && object.metadata() != Js.UNDEFINED) {
            JsObject copy = object.copy();
            copy.defineOwn(METADATA_KEY, object.metadata());
            return copy;
        }
        return value;
    }
}
