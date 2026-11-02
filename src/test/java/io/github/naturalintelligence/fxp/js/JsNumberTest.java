// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsNumberTest {
    @Test
    void printsNumbersAsJavaScriptDoes() {
        assertEquals("1e+21", JsNumber.toString(1e21));
        assertEquals("123456789012345680000", JsNumber.toString(123456789012345680000.0));
        assertEquals("1e-7", JsNumber.toString(1e-7));
        assertEquals("0.000001", JsNumber.toString(0.000001));
        assertEquals("5e-324", JsNumber.toString(Double.MIN_VALUE));
        assertEquals("0", JsNumber.toString(-0.0));
        assertEquals("-1.5", JsNumber.toString(-1.5));
        assertEquals("0.1", JsNumber.toString(0.1));
        assertEquals("NaN", JsNumber.toString(Double.NaN));
    }

    @Test
    void convertsStringsAsToNumberDoes() {
        assertEquals(31.0, JsNumber.stringToNumber(" 0x1F "));
        assertEquals(0.0, JsNumber.stringToNumber(""));
        assertEquals(Double.NaN, JsNumber.stringToNumber("1_0"));
        assertEquals(0.5, JsNumber.stringToNumber(".5"));
        assertEquals(Double.POSITIVE_INFINITY, JsNumber.stringToNumber("Infinity"));
        assertEquals(Double.NaN, JsNumber.stringToNumber("-0x10"));
    }

    @Test
    void parsesIntegersAsParseIntDoes() {
        assertEquals(-31.0, JsNumber.parseInt("-0x1F", 16));
        assertEquals(12.0, JsNumber.parseInt(" 12abc", 10));
        assertEquals(Double.NaN, JsNumber.parseInt("abc", 10));
        assertEquals(65.0, JsNumber.parseInt("0x41", 16));
    }
}
