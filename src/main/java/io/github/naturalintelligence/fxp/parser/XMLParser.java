// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/xmlparser/XMLParser.js).

package io.github.naturalintelligence.fxp.parser;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.Utf8;
import io.github.naturalintelligence.fxp.validator.XMLValidator;

/**
 * Parses XML into JavaScript-style values ({@link JsObject}, {@link JsArray}, strings, numbers, booleans), as
 * fast-xml-parser's {@code XMLParser} does, under the same options.
 */
public final class XMLParser {
    private final JsObject externalEntities = new JsObject();
    private final JsObject options;

    /**
     * {@code new XMLParser(options)}.
     *
     * @param options the options, any value ({@code undefined} for the defaults)
     * @throws io.github.naturalintelligence.fxp.js.JsError when an option is rejected
     */
    public XMLParser(Object options) {
        this.options = OptionsBuilder.build(options);
    }

    // The string a non-string input becomes: xmlData.toString().
    private static String toXmlString(Object xmlData) {
        if (xmlData instanceof byte[] bytes) {
            return Utf8.decode(bytes, bytes.length);
        }
        Object toString = Js.get(xmlData, "toString");
        if (xmlData instanceof JsObject object && object.hasOwn("toString")) {
            if (!Js.truthy(toString)) {
                throw JsError.error("XML data is accepted in String or Bytes[] form.");
            }
            if (!(toString instanceof JsFunction function)) {
                throw JsError.typeError("xmlData.toString is not a function");
            }
            return Js.toStr(function.call());
        }
        return Js.toStr(xmlData);
    }

    /**
     * {@code parser.parse(xmlData, validationOption)}.
     *
     * @param xmlData a string, the bytes of a UTF-8 document ({@code Uint8Array}), or any value converted with
     *     {@code toString()}
     * @param validationOption true or an object of validator options to validate first; undefined or false to skip
     * @return the parsed value: a {@link JsObject}, or with {@code preserveOrder} a {@link JsArray}
     * @throws io.github.naturalintelligence.fxp.js.JsError when the input or the options are rejected
     */
    public Object parse(Object xmlData, Object validationOption) {
        String xml = xmlData instanceof String s ? s : toXmlString(xmlData);
        if (Js.truthy(validationOption)) {
            Object validatorOptions = Js.strictEquals(validationOption, true) ? new JsObject() : validationOption;
            Object result = XMLValidator.validate(xml, validatorOptions);
            if (!Js.strictEquals(result, true)) {
                Object err = Js.get(result, "err");
                throw JsError.error(Js.toStr(Js.get(err, "msg")) + ":" + Js.toStr(Js.get(err, "line")) + ":"
                        + Js.toStr(Js.get(err, "col")));
            }
        }
        OrderedObjParser orderedObjParser = new OrderedObjParser(options, externalEntities);
        JsArray orderedResult = orderedObjParser.parseXml(xml);
        if (Js.truthy(options.get("preserveOrder"))) {
            return orderedResult;
        }
        return Node2Json.prettify(orderedResult, options, orderedObjParser.matcher(),
                orderedObjParser.readonlyMatcher());
    }

    /**
     * {@code parser.parse(xmlData)}.
     *
     * @param xmlData the input
     * @return the parsed value
     */
    public Object parse(Object xmlData) {
        return parse(xmlData, Js.UNDEFINED);
    }

    private static boolean contains(Object target, String search, String text) {
        if (target instanceof String s) {
            return s.contains(search);
        }
        if (target instanceof JsArray array) {
            return array.items().stream().anyMatch(item -> Js.strictEquals(item, search));
        }
        Js.call(Js.get(target, "indexOf"), text);
        return false;
    }

    /**
     * {@code parser.addEntity(key, value)}: an extra named entity for every later parse.
     *
     * @param key the entity name, without {@code &} and {@code ;}
     * @param value its replacement text
     * @throws io.github.naturalintelligence.fxp.js.JsError when the name or the value is rejected
     */
    public void addEntity(Object key, Object value) {
        if (contains(value, "&", "value.indexOf")) {
            throw JsError.error("Entity value can't have '&'");
        } else if (contains(key, "&", "key.indexOf") || contains(key, ";", "key.indexOf")) {
            throw JsError.error("An entity must be set without '&' and ';'. Eg. use '#xD' for '&#xD;'");
        } else if (Js.strictEquals(value, "&")) {
            throw JsError.error("An entity with value '&' is not permitted");
        }
        externalEntities.set(Js.toStr(key), value);
    }
}
