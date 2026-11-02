// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CommandTest {
    private static String answer(String request) {
        return Command.answer(request).line();
    }

    @Test
    void answersEachNonEmptyLineInOrder() {
        Command.Output output = Command.run("{\"op\":\"parse\",\"xml\":\"<a>1</a>\"}\n\n"
                + "{\"op\":\"validate\",\"xml\":\"<a/>\"}\r\n");
        assertEquals("{\"result\":{\"a\":1}}\n{\"result\":true}\n", output.stdout());
        assertEquals(0, output.status());
    }

    @Test
    void exitsWithOneWhenARequestFails() {
        Command.Output output = Command.run("{\"op\":\"nope\"}\n{\"op\":\"build\",\"data\":{\"a\":1}}\n");
        assertEquals("{\"error\":{\"name\":\"RequestError\",\"message\":\"unknown op: \\\"nope\\\"\"}}\n"
                + "{\"result\":\"<a>1</a>\"}\n", output.stdout());
        assertEquals(1, output.status());
    }

    @Test
    void reportsJsonSyntaxErrorsAsV8Does() {
        assertEquals("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"Expected property name or '}' in JSON at "
                + "position 1 (line 1 column 2)\"}}", answer("{"));
        assertEquals("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"Unexpected end of JSON input\"}}",
                answer("[1,"));
        assertEquals("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"Unexpected token 'x', \\\"x{\\\"op\\\":\\\""
                + "pa\\\"... is not valid JSON\"}}", answer("x{\"op\":\"parse\",\"xml\":\"<a>xxxxxxxx</a>\"}"));
        assertEquals("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"\\\"undefined\\\" is not valid JSON\"}}",
                answer("undefined"));
        assertEquals("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"Unexpected non-whitespace character after "
                + "JSON at position 2 (line 1 column 3)\"}}", answer("1 2"));
    }

    @Test
    void rejectsMalformedRequests() {
        assertEquals("{\"error\":{\"name\":\"RequestError\",\"message\":\"a request must be a JSON object\"}}",
                answer("[]"));
        assertEquals("{\"error\":{\"name\":\"RequestError\",\"message\":\"bytes must be an array of integers from 0 "
                + "to 255\"}}", answer("{\"op\":\"parse\",\"bytes\":[256]}"));
        assertEquals("{\"error\":{\"name\":\"RequestError\",\"message\":\"$fn must be an object\"}}",
                answer("{\"op\":\"parse\",\"xml\":\"<a/>\",\"options\":{\"isArray\":{\"$fn\":1}}}"));
    }

    @Test
    void logsCallbackCallsWithMatchers() {
        // the matcher keeps its sibling counts from parsing, so the root is the second "r" at its level
        assertEquals("{\"result\":{\"r\":{\"a\":[1]}},\"calls\":[[\"isArray\",\"a\",{\"$matcher\":{\"path\":"
                + "\"r.a\",\"tag\":\"a\",\"depth\":2,\"position\":0,\"counter\":0}},true],[\"isArray\",\"r\","
                + "{\"$matcher\":{\"path\":\"r\",\"tag\":\"r\",\"depth\":1,\"position\":1,\"counter\":1}},false]]}",
                answer("{\"op\":\"parse\",\"xml\":\"<r><a>1</a></r>\",\"options\":{\"jPath\":false,"
                        + "\"isArray\":{\"$fn\":{\"log\":true,\"byExpr\":[[\"..a\",true]],\"else\":false}}}}"));
    }

    @Test
    void writesParseMetadataAsAnExtraMember() {
        assertEquals("{\"result\":[{\"a\":[],\"@@xmlMetadata\":{\"startIndex\":0,\"endIndex\":4}}]}",
                answer("{\"op\":\"parse\",\"xml\":\"<a/>\",\"options\":{\"preserveOrder\":true,"
                        + "\"captureMetaData\":true}}"));
    }

    @Test
    void runsTheDeprecatedFxparserCommand() {
        String warning = "\\u001b[33m\u26a0\ufe0f  Warning: The built-in CLI interface is now deprecated.\\u001b[0m"
                + "\\nPlease install the dedicated CLI package instead:\\n  npm install -g fxp-cli\\n";
        assertEquals("{\"stdout\":\"{\\n    \\\"a\\\": {\\n        \\\"#text\\\": 2,\\n        \\\"@_x\\\": 1\\n"
                + "    }\\n}\\n\",\"stderr\":\"" + warning + "\",\"exitCode\":0}",
                answer("{\"op\":\"cli\",\"input\":\"<a x=\\\"1\\\">2</a>\"}"));
        assertEquals("{\"stdout\":\"{\\n  err: {\\n    code: 'InvalidXml',\\n    msg: 'Start tag expected.',\\n"
                + "    line: 1,\\n    col: undefined\\n  }\\n}\\n\",\"stderr\":\"" + warning + "\",\"exitCode\":1}",
                answer("{\"op\":\"cli\",\"args\":[\"-V\"],\"input\":\"\"}"));
    }

    @Test
    void printsUsageForAnyArgument() throws IOException, InterruptedException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(List.of(java.toString(), "-cp", System.getProperty("java.class.path"),
                Main.class.getName(), "--help")).redirectInput(ProcessBuilder.Redirect.PIPE).start();
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(60, TimeUnit.SECONDS);
        assertEquals(2, process.exitValue());
        assertEquals("", stdout);
        assertEquals(Main.USAGE, stderr);
    }
}
