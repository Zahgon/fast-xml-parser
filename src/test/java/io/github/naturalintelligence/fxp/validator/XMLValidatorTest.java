// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (spec/validator_spec.js, spec/validator_utf8_with_BOM_spec.js).

package io.github.naturalintelligence.fxp.validator;

import static io.github.naturalintelligence.fxp.TestJson.json;
import static io.github.naturalintelligence.fxp.TestJson.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.naturalintelligence.fxp.js.Js;
import org.junit.jupiter.api.Test;

class XMLValidatorTest {
    private static String validate(String xml) {
        return json(XMLValidator.validate(xml, Js.UNDEFINED));
    }

    private static String error(String code, String msg, int line, int col) {
        return "{\"err\":{\"code\":\"" + code + "\",\"msg\":\"" + msg + "\",\"line\":" + line + ",\"col\":" + col
                + "}}";
    }

    @Test
    void acceptsWellFormedDocuments() {
        assertEquals("true", validate("<?xml version=\"1.0\"?><a x=\"1\"><b/><![CDATA[x]]><!-- c --></a>"));
        assertEquals("true", validate("﻿<a>&amp;&#65;&#x41;</a>"));
    }

    @Test
    void reportsMismatchedClosingTags() {
        assertEquals(error("InvalidTag", "Expected closing tag 'b' (opened in line 1, col 4) instead of closing tag "
                + "'a'.", 1, 7), validate("<a><b></a>"));
        assertEquals(error("InvalidTag", "Closing tag 'a' has not been opened.", 1, 1), validate("</a>"));
    }

    @Test
    void reportsUnclosedTags() {
        assertEquals(error("InvalidTag", "Unclosed tag 'a'.", 1, 1), validate("<a>"));
        assertEquals("{\"err\":{\"code\":\"InvalidXml\",\"msg\":\"Invalid '[    \\\"a\\\",    \\\"b\\\"]' found.\","
                + "\"line\":1,\"col\":1}}", validate("<a><b>"));
        assertEquals("{\"err\":{\"code\":\"InvalidXml\",\"msg\":\"Start tag expected.\",\"line\":1}}", validate(""));
    }

    @Test
    void reportsAttributeProblemsAtTheirPosition() {
        assertEquals(error("InvalidAttr", "Attribute 'b' is repeated.", 1, 10), validate("<a b='1' b='2'></a>"));
        assertEquals(error("InvalidAttr", "boolean attribute 'b' is not allowed.", 1, 4), validate("<a b/>"));
        assertEquals("true", json(XMLValidator.validate("<a b/>", options("{\"allowBooleanAttributes\":true}"))));
        assertEquals(error("InvalidAttr", "Attribute 'c' has no space in starting.", 2, 8),
                validate("<a\n  b=\"1\"c=\"2\"/>"));
    }

    @Test
    void reportsBadCharactersAndExtraContent() {
        assertEquals(error("InvalidChar", "char '&' is not expected.", 1, 4), validate("<a>&</a>"));
        assertEquals(error("InvalidXml", "Extra text at the end", 1, 8), validate("<a></a>x"));
        // a self-closing root never marks the root as reached, so the trailing text passes
        assertEquals("true", validate("<a/>x"));
        assertEquals(error("InvalidXml", "Multiple possible root nodes found.", 1, 10), validate("<a></a><b></b>"));
        // only an opening tag checks for a second root
        assertEquals("true", validate("<a></a><b/>"));
    }

    @Test
    void acceptsUnpairedTagsWhenListed() {
        assertEquals("true", json(XMLValidator.validate("<a><br></a>", options("{\"unpairedTags\":[\"br\"]}"))));
    }
}
