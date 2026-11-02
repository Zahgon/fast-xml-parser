// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/cli/cli.js), without its file system arguments.

package io.github.naturalintelligence.fxp.cli;

import io.github.naturalintelligence.fxp.js.Inspect;
import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.Json;
import io.github.naturalintelligence.fxp.parser.XMLParser;
import io.github.naturalintelligence.fxp.validator.XMLValidator;
import java.util.function.UnaryOperator;

/** The {@code cli} op: what the deprecated {@code fxparser} command writes for some flags and standard input. */
final class FxParserCommand {
    private static final String DEPRECATION = "\u001b[33m⚠️  Warning: The built-in CLI interface is now "
            + "deprecated.\u001b[0m\nPlease install the dedicated CLI package instead:\n  npm install -g fxp-cli\n";

    private FxParserCommand() {
    }

    static JsObject run(JsObject request) {
        Object args = request.get("args");
        if (args == Js.UNDEFINED) {
            args = new JsArray();
        }
        if (!(args instanceof JsArray list) || !list.items().stream().allMatch(arg -> arg instanceof String)) {
            throw RequestHandler.requestError("args must be an array of strings");
        }
        if (!(request.get("input") instanceof String input)) {
            throw RequestHandler.requestError("input must be a string");
        }
        JsObject options = new JsObject();
        options.set("removeNSPrefix", true);
        options.set("ignoreAttributes", false);
        options.set("parseTagValue", true);
        options.set("parseAttributeValue", true);
        boolean validate = false;
        boolean validateOnly = false;
        for (Object arg : list) {
            switch ((String) arg) {
                case "-ns" -> options.set("removeNSPrefix", false);
                case "-a" -> options.set("ignoreAttributes", true);
                case "-c" -> {
                    options.set("parseTagValue", false);
                    options.set("parseAttributeValue", false);
                }
                case "-v" -> validate = true;
                case "-V" -> validateOnly = true;
                default -> throw RequestHandler.requestError("unsupported fxparser argument: " + arg);
            }
        }
        Object output;
        int exitCode = 0;
        if (validateOnly) {
            output = XMLValidator.validate(input, Js.UNDEFINED);
            exitCode = Js.strictEquals(output, true) ? 0 : 1;
        } else {
            XMLParser parser = new XMLParser(options);
            output = Json.stringify(parser.parse(input, validate), "    ", UnaryOperator.identity());
        }
        JsObject response = new JsObject();
        response.set("stdout", Inspect.format(output) + "\n");
        response.set("stderr", DEPRECATION);
        response.set("exitCode", (double) exitCode);
        return response;
    }
}
