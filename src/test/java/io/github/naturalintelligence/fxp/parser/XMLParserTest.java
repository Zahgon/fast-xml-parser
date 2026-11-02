// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (spec/xmlParser_spec.js, spec/attr_spec.js, spec/data_spec.js,
// spec/cdata_spec.js, spec/comments_spec.js, spec/pi_spec.js, spec/stopNodes_spec.js, spec/unpairedTags_spec.js,
// spec/arrayMode_spec.js, spec/inputType_spec.js, spec/startIndex_spec.js and spec/nfr_spec.js).

package io.github.naturalintelligence.fxp.parser;

import static io.github.naturalintelligence.fxp.TestJson.json;
import static io.github.naturalintelligence.fxp.TestJson.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class XMLParserTest {
    private static String parse(String optionsJson, String xml) {
        return json(new XMLParser(optionsJson == null ? Js.UNDEFINED : options(optionsJson)).parse(xml));
    }

    @Test
    void parsesStringsIntegersBooleansFloatsAndHexadecimals() {
        String xml = """
                <rootNode>
                <tag>value</tag>
                <boolean>true</boolean>
                <intTag>045</intTag>
                <floatTag>65.34</floatTag>
                <hexadecimal>0x15</hexadecimal>
                </rootNode>""";
        assertEquals("{\"rootNode\":{\"tag\":\"value\",\"boolean\":true,\"intTag\":45,\"floatTag\":65.34,"
                + "\"hexadecimal\":21}}", parse(null, xml));
    }

    @Test
    void parsesOnlyTrueNumbersWithoutLeadingZeros() {
        String xml = "<rootNode><intTag>045</intTag><floatTag>65.340</floatTag>"
                + "<long>420926189200190257681175017717</long></rootNode>";
        assertEquals("{\"rootNode\":{\"intTag\":\"045\",\"floatTag\":65.34,\"long\":4.209261892001902e+29}}",
                parse("{\"numberParseOptions\":{\"leadingZeros\":false}}", xml));
    }

    @Test
    void keepsTagValuesAsStringsWhenParseTagValueIsOff() {
        assertEquals("{\"a\":{\"b\":\"01\",\"c\":\"true\"}}",
                parse("{\"parseTagValue\":false}", "<a><b>01</b><c>true</c></a>"));
    }

    @Test
    void groupsRepeatedTagsIntoArrays() {
        assertEquals("{\"root\":{\"item\":[1,2,\"x\"]}}",
                parse(null, "<root><item>1</item><item>2</item><item>x</item></root>"));
    }

    @Test
    void ordersIntegerKeysFirstAsJavaScriptObjectsDo() {
        assertEquals("{\"root\":{\"1\":\"y\",\"2\":\"x\",\"b\":1,\"a\":\"\"}}",
                parse(null, "<root><b>1</b><2>x</2><a/><1>y</1></root>"));
    }

    @Test
    void readsAttributesWithPrefixAndGroupName() {
        String xml = "<a x=\"1\" y='two' flag>text</a>";
        assertEquals("{\"a\":{\"#text\":\"text\",\"@_x\":\"1\",\"@_y\":\"two\",\"@_flag\":true}}",
                parse("{\"ignoreAttributes\":false,\"allowBooleanAttributes\":true}", xml));
        assertEquals("{\"a\":{\"#text\":\"text\",\"$\":{\"x\":1,\"y\":\"two\"}}}",
                parse("{\"ignoreAttributes\":false,\"attributeNamePrefix\":\"\",\"attributesGroupName\":\"$\","
                        + "\"parseAttributeValue\":true}", xml));
    }

    @Test
    void removesNamespacePrefixes() {
        String xml = "<soap:Envelope xmlns:soap=\"u\" soap:id=\"1\"><soap:Body>x</soap:Body></soap:Envelope>";
        assertEquals("{\"Envelope\":{\"Body\":\"x\",\"@_id\":\"1\"}}",
                parse("{\"ignoreAttributes\":false,\"removeNSPrefix\":true}", xml));
    }

    @Test
    void keepsCdataAndCommentsUnderTheirOwnNames() {
        String xml = "<a>t<![CDATA[<raw>]]><!-- note --></a>";
        assertEquals("{\"a\":{\"#cdata\":\"<raw>\",\"#comment\":\" note \",\"#text\":\"t\"}}",
                parse("{\"cdataPropName\":\"#cdata\",\"commentPropName\":\"#comment\"}", xml));
        assertEquals("{\"a\":\"t<raw>\"}", parse(null, xml));
    }

    @Test
    void preservesOrderOfMixedContent() {
        assertEquals("[{\"a\":[{\"b\":[{\"#text\":1}]},{\"#text\":\"t\"},{\"c\":[]}]}]",
                parse("{\"preserveOrder\":true}", "<a><b>1</b>t<c/></a>"));
    }

    @Test
    void readsProcessingInstructionsAndTheDeclaration() {
        String xml = "<?xml version=\"1.0\"?><?pi data?><a/>";
        assertEquals("{\"?xml\":{\"@_version\":\"1.0\"},\"?pi\":\"\",\"a\":\"\"}",
                parse("{\"ignoreAttributes\":false}", xml));
        assertEquals("{\"?pi\":\"\",\"a\":\"\"}", parse("{\"ignoreDeclaration\":true}", xml));
        assertEquals("{\"a\":\"\"}", parse("{\"ignorePiTags\":true}", xml));
    }

    @Test
    void keepsStopNodeContentRaw() {
        String options = "{\"stopNodes\":[\"*.script\",\"html.p\"]}";
        assertEquals("{\"html\":{\"script\":\"x = 1;\",\"p\":\"<b>x</b>\"}}",
                parse(options, "<html><script>x = 1;</script><p><b>x</b></p></html>"));
        // a '<' inside a stop node is read as the start of a tag, which swallows the closing tag
        JsError error = assertThrows(JsError.class,
                () -> parse(options, "<html><script>if (a < b) { x(); }</script></html>"));
        assertEquals("Unexpected end of script", error.getMessage());
    }

    @Test
    void closesUnpairedTagsImplicitly() {
        String xml = "<root><br><a>1</a><br/></root>";
        assertEquals("{\"root\":{\"br\":[\"\",\"\"],\"a\":1}}", parse("{\"unpairedTags\":[\"br\"]}", xml));
        JsError error = assertThrows(JsError.class,
                () -> parse("{\"unpairedTags\":[\"br\"]}", "<root><br></br></root>"));
        assertEquals("Unpaired tag can not be used as closing tag: </br>", error.getMessage());
    }

    @Test
    void makesSelectedTagsArraysThroughIsArray() {
        JsObject options = options("{}");
        List<String> paths = new ArrayList<>();
        options.set("isArray", (JsFunction) args -> {
            paths.add((String) args[1]);
            return "item".equals(args[0]);
        });
        Object result = new XMLParser(options).parse("<root><item>1</item><one>2</one></root>");
        assertEquals("{\"root\":{\"item\":[1],\"one\":2}}", json(result));
        assertEquals(List.of("root.item", "root.one", "root"), paths);
    }

    @Test
    void passesValuesThroughTheProcessors() {
        JsObject options = options("{\"ignoreAttributes\":false}");
        options.set("tagValueProcessor", (JsFunction) args -> "b".equals(args[0]) ? null : "<" + args[1] + ">");
        options.set("attributeValueProcessor", (JsFunction) args -> args[1]);
        Object result = new XMLParser(options).parse("<a id=\"7\"><b>1</b><c>2</c></a>");
        assertEquals("{\"a\":{\"b\":\"1\",\"c\":\"<2>\",\"@_id\":\"7\"}}", json(result));
    }

    @Test
    void decodesXmlEntitiesAndLeavesNumericReferencesWithoutHtmlEntities() {
        String xml = "<a>&lt;&amp;&#65;&#x42;&nbsp;</a>";
        assertEquals("{\"a\":\"<&&#65;&#x42;&nbsp;\"}", parse(null, xml));
        assertEquals("{\"a\":\"<&AB \"}", parse("{\"htmlEntities\":true}", xml));
    }

    @Test
    void expandsDoctypeEntities() {
        String xml = "<!DOCTYPE r [<!ENTITY e \"value\">]><r>&e;</r>";
        assertEquals("{\"r\":\"value\"}", parse(null, xml));
    }

    @Test
    void addsExternalEntities() {
        XMLParser parser = new XMLParser(Js.UNDEFINED);
        parser.addEntity("brand", "Acme");
        assertEquals("{\"a\":\"Acme\"}", json(parser.parse("<a>&brand;</a>")));
        JsError error = assertThrows(JsError.class, () -> parser.addEntity("bad", "a&b"));
        assertEquals("Entity value can't have '&'", error.getMessage());
    }

    @Test
    void decodesUtf8BytesAndKeepsTheByteOrderMark() {
        String xml = "﻿<root>café</root>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        assertEquals("{\"root\":\"café\"}", json(new XMLParser(Js.UNDEFINED).parse(bytes)));
        Object ordered = new XMLParser(options("{\"preserveOrder\":true,\"captureMetaData\":true}")).parse(bytes);
        JsObject root = (JsObject) ((JsArray) ordered).get(0);
        assertEquals("{\"startIndex\":1,\"endIndex\":" + xml.length() + "}", json(root.metadata()));
    }

    @Test
    void capturesStartAndEndIndices() {
        Object result = new XMLParser(options("{\"preserveOrder\":true,\"captureMetaData\":true}"))
                .parse("<r><a>x</a></r>");
        JsObject root = (JsObject) ((JsArray) result).get(0);
        assertEquals("{\"startIndex\":0,\"endIndex\":15}", json(root.metadata()));
    }

    @Test
    void validatesBeforeParsingWhenAsked() {
        JsError error = assertThrows(JsError.class, () -> new XMLParser(Js.UNDEFINED).parse("<a>", true));
        assertEquals("Unclosed tag 'a'.:1:1", error.getMessage());
    }

    @Test
    void rejectsPrototypePollutingNames() {
        JsError error = assertThrows(JsError.class, () -> parse(null, "<__proto__>x</__proto__>"));
        assertEquals("[SECURITY] Invalid name: \"__proto__\" is a reserved JavaScript keyword that could cause "
                + "prototype pollution", error.getMessage());
        assertEquals("{\"a\":{\"__toString\":1}}", parse(null, "<a><toString>1</toString></a>"));
        JsError option = assertThrows(JsError.class, () -> parse("{\"textNodeName\":\"Constructor\"}", "<a/>"));
        assertEquals("[SECURITY] Invalid textNodeName: \"Constructor\" is a reserved JavaScript keyword that could "
                + "cause prototype pollution", option.getMessage());
    }

    @Test
    void limitsNesting() {
        assertEquals("{\"a\":{\"b\":{\"c\":\"\"}}}", parse("{\"maxNestedTags\":1}", "<a><b><c/></b></a>"));
        JsError error = assertThrows(JsError.class,
                () -> parse("{\"maxNestedTags\":1}", "<a><b><c><d/></c></b></a>"));
        assertEquals("Maximum nested tags exceeded", error.getMessage());
    }
}
