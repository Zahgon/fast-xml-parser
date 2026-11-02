// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Tests of the ported dependencies: strnum, anynum, @nodable/entities, path-expression-matcher and xml-naming.

package io.github.naturalintelligence.fxp;

import static io.github.naturalintelligence.fxp.TestJson.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.naturalintelligence.fxp.entities.EntityDecoder;
import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.naming.XmlNaming;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.pathexpr.Matcher;
import io.github.naturalintelligence.fxp.strnum.AnyNum;
import io.github.naturalintelligence.fxp.strnum.StrNum;
import org.junit.jupiter.api.Test;

class DependenciesTest {
    @Test
    void strnumParsesOnlyFaithfulNumbers() {
        assertEquals(31.0, StrNum.toNumber("0x1F", Js.UNDEFINED));
        assertEquals(1.5, StrNum.toNumber("1.50", Js.UNDEFINED));
        assertEquals("0.1000000000000000055511151231257827", StrNum.toNumber("0.1000000000000000055511151231257827",
                Js.UNDEFINED));
        assertEquals("007", StrNum.toNumber("007", options("{\"leadingZeros\":false}")));
        assertEquals("1e400", StrNum.toNumber("1e400", Js.UNDEFINED));
        assertNull(StrNum.toNumber("abc", options("{\"infinity\":\"null\"}")));
        assertEquals(-123.0, StrNum.toNumber("−١٢٣", options("{\"unicode\":true}")));
    }

    @Test
    void anynumNormalisesDigitsOfOtherScripts() {
        assertEquals("12-3", AnyNum.anynum("１２−3"));
        assertEquals("0", AnyNum.anynum("𝟎"));
    }

    @Test
    void entityDecoderClassifiesNumericReferences() {
        EntityDecoder decoder = new EntityDecoder(options("{\"ncr\":{\"onNCR\":\"leave\"}}"));
        assertEquals("&#65;&lt", decoder.decode("&#65;&lt"));
        assertEquals("<", decoder.decode("&lt;"));
        assertEquals("", new EntityDecoder(Js.UNDEFINED).decode("&#1;"));
        EntityDecoder strict = new EntityDecoder(options("{\"ncr\":{\"nullNCR\":\"throw\"}}"));
        assertEquals("[EntityDecoder] Prohibited numeric character reference &#0; (U+0000)",
                assertThrows(JsError.class, () -> strict.decode("&#0;")).getMessage());
    }

    @Test
    void entityDecoderEnforcesExpansionLimits() {
        EntityDecoder decoder = new EntityDecoder(options("{\"limit\":{\"maxTotalExpansions\":1,"
                + "\"applyLimitsTo\":\"all\"}}"));
        assertEquals("[EntityReplacer] Entity expansion count limit exceeded: 2 > 1",
                assertThrows(JsError.class, () -> decoder.decode("&lt;&gt;")).getMessage());
    }

    @Test
    void matcherTracksPositionsAndCounters() {
        Matcher matcher = new Matcher();
        matcher.push("root");
        matcher.push("item", new JsObject(), null);
        matcher.pop();
        matcher.push("other");
        matcher.pop();
        JsObject attrs = new JsObject();
        attrs.set("id", "7");
        matcher.push("item", attrs, null);
        assertEquals("root.item", matcher.toString());
        assertEquals(2.0, matcher.getPosition());
        assertEquals(1.0, matcher.getCounter());
        assertTrue(matcher.matches(new Expression("..item[id=7]:nth(1)")));
        assertFalse(matcher.matches(new Expression("root.item:first")));
        assertTrue(matcher.matches(new Expression("root.*")));
    }

    @Test
    void expressionsRejectEmptyParts() {
        assertEquals("Invalid namespace in pattern: ::a",
                assertThrows(JsError.class, () -> new Expression("::a")).getMessage());
    }

    @Test
    void qNamesFollowXml10() {
        assertTrue(XmlNaming.qName("ns:local"));
        assertFalse(XmlNaming.qName("a:b:c"));
        assertFalse(XmlNaming.qName("1a"));
        assertFalse(XmlNaming.qName("𐀀"));
        assertTrue(XmlNaming.qName11("𐀀"));
    }
}
