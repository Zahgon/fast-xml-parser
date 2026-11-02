// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/fast-xml-builder)
// Ported to Java from fast-xml-parser 5.11.2 (spec/html_spec.js, spec/pathExpression_spec.js) and the behaviour of
// fast-xml-builder 1.2.0.

package io.github.naturalintelligence.fxp.builder;

import static io.github.naturalintelligence.fxp.TestJson.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.Json;
import io.github.naturalintelligence.fxp.parser.XMLParser;
import org.junit.jupiter.api.Test;

class XMLBuilderTest {
    private static Object build(String optionsJson, String dataJson) {
        return new XMLBuilder(optionsJson == null ? Js.UNDEFINED : options(optionsJson)).build(Json.parse(dataJson));
    }

    @Test
    void buildsTagsTextAndRepeatedTags() {
        assertEquals("<root><a>1</a><a>x</a><b></b><c/></root>",
                build(null, "{\"root\":{\"a\":[1,\"x\"],\"b\":\"\",\"c\":null}}"));
    }

    @Test
    void escapesTextAndAttributes() {
        assertEquals("<a q=\"&amp;&quot;&apos;\">&lt;b&gt; &amp; it&apos;s</a>",
                build("{\"ignoreAttributes\":false}", "{\"a\":{\"@_q\":\"&\\\"'\",\"#text\":\"<b> & it's\"}}"));
    }

    @Test
    void writesNumbersAsJavaScriptPrintsThem() {
        assertEquals("<a>1e+21</a><b>0</b><c>1e-7</c><d>123456789012345680000</d>",
                build(null, "{\"a\":1e21,\"b\":-0,\"c\":1e-7,\"d\":123456789012345680000}"));
    }

    @Test
    void formatsWithIndentation() {
        assertEquals("<root>\n  <a>1</a>\n  <b>\n    <c>2</c>\n  </b>\n</root>\n",
                build("{\"format\":true}", "{\"root\":{\"a\":1,\"b\":{\"c\":2}}}"));
    }

    @Test
    void suppressesEmptyAndBooleanAttributes() {
        assertEquals("<a on><e/><br></a>", build("{\"ignoreAttributes\":false,\"suppressEmptyNode\":true,"
                + "\"unpairedTags\":[\"br\"]}", "{\"a\":{\"@_on\":true,\"e\":\"\",\"br\":\"\"}}"));
    }

    @Test
    void writesCdataCommentsAndDeclarations() {
        assertEquals("<?xml version=\"1.0\"?><a><![CDATA[x]]]]><![CDATA[>y]]><!--a- -b- --></a>",
                build("{\"ignoreAttributes\":false,\"cdataPropName\":\"#cdata\",\"commentPropName\":\"#comment\"}",
                        "{\"?xml\":{\"@_version\":\"1.0\"},\"a\":{\"#cdata\":\"x]]>y\",\"#comment\":\"a--b-\"}}"));
    }

    @Test
    void keepsStopNodesRaw() {
        assertEquals("<html><script>a < b && c</script></html>",
                build("{\"stopNodes\":[\"*.script\"]}", "{\"html\":{\"script\":\"a < b && c\"}}"));
    }

    @Test
    void buildsBackWhatThePreserveOrderParserProduces() {
        String xml = "<a x=\"1\"><b>t</b>mixed<c/><!--n--></a>";
        Object ordered = new XMLParser(options("{\"preserveOrder\":true,\"ignoreAttributes\":false,"
                + "\"commentPropName\":\"#comment\"}")).parse(xml);
        Object rebuilt = new XMLBuilder(options("{\"preserveOrder\":true,\"ignoreAttributes\":false,"
                + "\"commentPropName\":\"#comment\"}")).build(ordered);
        assertEquals("<a x=\"1\"><b>t</b>mixed<c></c><!--n--></a>", rebuilt);
    }

    @Test
    void reportsTheOriginalsErrors() {
        JsError nullData = assertThrows(JsError.class, () -> build(null, "null"));
        assertEquals("Cannot read properties of null (reading '?xml')", nullData.getMessage());
        JsError indent = assertThrows(JsError.class, () -> build("{\"format\":true,\"indentBy\":2}", "{\"a\":1}"));
        assertEquals("this.options.indentBy.repeat is not a function", indent.getMessage());
    }
}
