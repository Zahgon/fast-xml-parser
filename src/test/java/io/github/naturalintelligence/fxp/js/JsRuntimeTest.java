// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsRuntimeTest {
    @Test
    void stringifiesAsJsonStringifyDoes() {
        Object value = Json.parse("{\"b\":1,\"2\":[true,null,\"\\u0001\\ud800\"],\"a\":{},\"1\":-0}");
        assertEquals("{\"1\":0,\"2\":[true,null,\"\\u0001\\ud800\"],\"b\":1,\"a\":{}}", Json.stringify(value));
        assertEquals("{\n    \"x\": [\n        1\n    ],\n    \"y\": []\n}",
                Json.stringify(Json.parse("{\"x\":[1],\"y\":[]}"), "    ", v -> v));
    }

    @Test
    void reportsJsonErrorsWithPositions() {
        JsError error = assertThrows(JsError.class, () -> Json.parse("{\"a\":1,\r\"b\"}"));
        assertEquals("Expected ':' after property name in JSON at position 11 (line 2 column 4)", error.getMessage());
        assertEquals("Bad control character in string literal in JSON at position 2 (line 1 column 3)",
                assertThrows(JsError.class, () -> Json.parse("\"a\tb\"")).getMessage());
    }

    @Test
    void translatesRegularExpressionsWithJavaScriptSemantics() {
        assertTrue(JsRegExp.create("^\\s$", "").test(" "));
        assertFalse(JsRegExp.create("^.$", "").test("\n"));
        assertTrue(JsRegExp.create("^.$", "s").test("\n"));
        assertFalse(JsRegExp.create("a$", "").test("a\n"));
        assertTrue(JsRegExp.create("^(a)?\\1b$", "").test("b"));
        assertTrue(JsRegExp.create("a{", "").test("a{"));
        assertEquals("Invalid regular expression: /(/my: Unterminated group",
                assertThrows(JsError.class, () -> JsRegExp.create("(", "ym")).getMessage());
    }

    @Test
    void keepsLastIndexBetweenTestsOfAGlobalExpression() {
        JsRegExp regexp = JsRegExp.create("^x", "g");
        assertTrue(regexp.test("xa"));
        assertFalse(regexp.test("xb"));
        assertTrue(regexp.test("xc"));
    }

    @Test
    void replacesWithSubstitutionPatterns() {
        assertEquals("x[&|&|x]y", JsRegExp.replace("x&y", JsRegExp.create("(&)", "g"), "[$1|$&|$`]"));
        assertEquals("-a-a-", JsRegExp.replace("aa", JsRegExp.create("x?", "g"), "-"));
        assertEquals("ab5", JsRegExp.replace("abundefined", Js.UNDEFINED, 5.0));
    }

    @Test
    void decodesUtf8AsTextDecoderDoes() {
        byte[] bytes = {'a', (byte) 0xff, (byte) 0xe2, (byte) 0x82, 'b', (byte) 0xef, (byte) 0xbb, (byte) 0xbf};
        assertEquals("a��b﻿", Utf8.decode(bytes, bytes.length));
        byte[] valid = "café 🧪".getBytes(StandardCharsets.UTF_8);
        assertEquals("café 🧪", Utf8.decode(valid, valid.length));
    }

    @Test
    void formatsValuesAsConsoleLogDoes() {
        JsObject err = new JsObject();
        err.set("code", "InvalidAttr");
        err.set("msg", "Attribute 'b' is repeated.");
        err.set("line", 1.0);
        err.set("col", 10.0);
        JsObject result = new JsObject();
        result.set("err", err);
        assertEquals("{\n  err: {\n    code: 'InvalidAttr',\n    msg: \"Attribute 'b' is repeated.\",\n    line: 1,\n"
                + "    col: 10\n  }\n}", Inspect.format(result));
        assertEquals("true", Inspect.format(true));
    }

    @Test
    void trimsJavaScriptWhiteSpace() {
        assertEquals("x", JsString.trim("  ﻿ x　 "));
        assertEquals("\u0085x", JsString.trim("\u0085x"));
    }
}
