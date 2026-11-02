// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (spec/*_spec.js): every call of the upstream suite, replayed.

package io.github.naturalintelligence.fxp.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Replays every library call the upstream jasmine suite makes, as a request of the command, against the response the
 * original gives (upstream-suite.jsonl: spec file, test title, request line, expected response line). Callback
 * options of the suite are replaced by logging callbacks with the library's default behaviour, so each response also
 * shows the arguments the callbacks receive.
 */
class UpstreamSuiteTest {
    private record Call(String spec, String test, String request, String expected) {
    }

    private static List<Call> calls() throws IOException {
        List<Call> calls = new ArrayList<>();
        try (InputStream in = UpstreamSuiteTest.class.getResourceAsStream("/upstream-suite.jsonl");
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsObject entry = (JsObject) Json.parse(line);
                calls.add(new Call((String) entry.get("spec"), (String) entry.get("test"),
                        (String) entry.get("request"), (String) entry.get("expected")));
            }
        }
        return calls;
    }

    @TestFactory
    Stream<DynamicNode> upstreamSuite() throws IOException {
        Map<String, List<DynamicNode>> bySpec = new LinkedHashMap<>();
        int index = 0;
        for (Call call : calls()) {
            String name = ++index + ": " + call.test();
            bySpec.computeIfAbsent(call.spec(), spec -> new ArrayList<>()).add(DynamicTest.dynamicTest(name,
                    () -> assertEquals(call.expected(), Command.answer(call.request()).line(),
                            () -> "response to " + call.request())));
        }
        return bySpec.entrySet().stream().map(e -> DynamicContainer.dynamicContainer(e.getKey(), e.getValue()));
    }
}
