// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/xmlparser/DocTypeReader.js).

package io.github.naturalintelligence.fxp.parser;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsNumber;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsString;
import io.github.naturalintelligence.fxp.naming.XmlNaming;

/** Reads a DOCTYPE declaration: its internal entities, with ELEMENT, ATTLIST and NOTATION declarations skipped. */
final class DocTypeReader {
    private final JsObject options;

    /** The entities read and the index of the DOCTYPE's closing '>'. */
    record Result(JsObject entities, int index) {
    }

    DocTypeReader(JsObject processEntities) {
        this.options = processEntities;
    }

    private static boolean charIs(String data, int index, char c) {
        return index >= 0 && index < data.length() && data.charAt(index) == c;
    }

    private static String charOrUndefined(String data, int index) {
        return index >= 0 && index < data.length() ? String.valueOf(data.charAt(index)) : "undefined";
    }

    private static boolean hasSeq(String data, String seq, int i) {
        for (int j = 0; j < seq.length(); j++) {
            if (!charIs(data, i + j + 1, seq.charAt(j))) {
                return false;
            }
        }
        return true;
    }

    private static int skipWhitespace(String data, int index) {
        while (index < data.length() && JsNumber.isWhiteSpace(data.charAt(index))) {
            index++;
        }
        return index;
    }

    private static void validateEntityName(String name) {
        if (!XmlNaming.qName(name)) {
            throw JsError.error("Invalid entity name " + name);
        }
    }

    private boolean limitApplies(String limit) {
        return !Js.strictEquals(options.get("enabled"), false) && !Js.isNullish(options.get(limit));
    }

    Result readDocType(String xmlData, int start) {
        JsObject entities = JsObject.withNullPrototype();
        double entityCount = 0;
        int i = start;
        if (!hasSeq(xmlData, "OCTYPE", i + 2)) {
            throw JsError.error("Invalid Tag instead of DOCTYPE");
        }
        i = i + 9;
        int angleBracketsCount = 1;
        boolean hasBody = false;
        boolean comment = false;
        char quoteChar = 0;
        for (; i < xmlData.length(); i++) {
            char c = xmlData.charAt(i);
            if (quoteChar != 0) {
                if (c == quoteChar) {
                    quoteChar = 0;
                }
                continue;
            }
            if (!hasBody && !comment && (c == '"' || c == '\'')) {
                quoteChar = c;
                continue;
            }
            if (c == '<' && !comment) {
                if (hasBody && hasSeq(xmlData, "!ENTITY", i)) {
                    i += 7;
                    String[] entity = new String[2];
                    i = readEntityExp(xmlData, i + 1, entity);
                    String val = entity[1];
                    if (val.indexOf('&') < 0) {
                        if (limitApplies("maxEntityCount")
                                && Js.greaterOrEqual(entityCount, options.get("maxEntityCount"))) {
                            throw JsError.error("Entity count (" + JsNumber.toString(entityCount + 1)
                                    + ") exceeds maximum allowed (" + Js.toStr(options.get("maxEntityCount")) + ")");
                        }
                        entities.set(entity[0], val);
                        entityCount++;
                    }
                } else if (hasBody && hasSeq(xmlData, "!ELEMENT", i)) {
                    i += 8;
                    i = readElementExp(xmlData, i + 1);
                } else if (hasBody && hasSeq(xmlData, "!ATTLIST", i)) {
                    i += 8;
                } else if (hasBody && hasSeq(xmlData, "!NOTATION", i)) {
                    i += 9;
                    i = readNotationExp(xmlData, i + 1);
                } else if (hasSeq(xmlData, "!--", i)) {
                    comment = true;
                } else {
                    throw JsError.error("Invalid DOCTYPE");
                }
                angleBracketsCount++;
            } else if (c == '>') {
                if (comment) {
                    if (charIs(xmlData, i - 1, '-') && charIs(xmlData, i - 2, '-')) {
                        comment = false;
                        angleBracketsCount--;
                    }
                } else {
                    angleBracketsCount--;
                }
                if (angleBracketsCount == 0) {
                    break;
                }
            } else if (c == '[') {
                hasBody = true;
            }
        }
        if (quoteChar != 0 || angleBracketsCount != 0) {
            throw JsError.error("Unclosed DOCTYPE");
        }
        return new Result(entities, i);
    }

    // Returns the index of the entity value's last character; fills name and value.
    private int readEntityExp(String xmlData, int start, String[] entity) {
        int i = skipWhitespace(xmlData, start);
        int nameStart = i;
        while (i < xmlData.length() && !JsNumber.isWhiteSpace(xmlData.charAt(i)) && xmlData.charAt(i) != '"'
                && xmlData.charAt(i) != '\'') {
            i++;
        }
        String entityName = xmlData.substring(nameStart, i);
        validateEntityName(entityName);
        i = skipWhitespace(xmlData, i);
        if (JsString.toUpperCase(xmlData.substring(i, Math.min(i + 6, xmlData.length()))).equals("SYSTEM")) {
            throw JsError.error("External entities are not supported");
        } else if (charIs(xmlData, i, '%')) {
            throw JsError.error("Parameter entities are not supported");
        }
        String[] value = new String[1];
        i = readIdentifierVal(xmlData, i, "entity", value);
        if (limitApplies("maxEntitySize") && Js.greaterThan((double) value[0].length(), options.get("maxEntitySize"))) {
            throw JsError.error("Entity \"" + entityName + "\" size (" + value[0].length()
                    + ") exceeds maximum allowed size (" + Js.toStr(options.get("maxEntitySize")) + ")");
        }
        entity[0] = entityName;
        entity[1] = value[0];
        return i - 1;
    }

    private static int readNotationExp(String xmlData, int start) {
        int i = skipWhitespace(xmlData, start);
        int nameStart = i;
        while (i < xmlData.length() && !JsNumber.isWhiteSpace(xmlData.charAt(i))) {
            i++;
        }
        validateEntityName(xmlData.substring(nameStart, i));
        i = skipWhitespace(xmlData, i);
        String identifierType = JsString.toUpperCase(xmlData.substring(Math.min(i, xmlData.length()),
                Math.min(i + 6, xmlData.length())));
        if (!identifierType.equals("SYSTEM") && !identifierType.equals("PUBLIC")) {
            throw JsError.error("Expected SYSTEM or PUBLIC, found \"" + identifierType + "\"");
        }
        i += identifierType.length();
        i = skipWhitespace(xmlData, i);
        String[] value = new String[1];
        if (identifierType.equals("PUBLIC")) {
            i = readIdentifierVal(xmlData, i, "publicIdentifier", value);
            i = skipWhitespace(xmlData, i);
            if (charIs(xmlData, i, '"') || charIs(xmlData, i, '\'')) {
                i = readIdentifierVal(xmlData, i, "systemIdentifier", value);
            }
        } else {
            i = readIdentifierVal(xmlData, i, "systemIdentifier", value);
            if (value[0].isEmpty()) {
                throw JsError.error("Missing mandatory system identifier for SYSTEM notation");
            }
        }
        return i - 1;
    }

    private static int readIdentifierVal(String xmlData, int start, String type, String[] value) {
        int i = start;
        String startChar = charOrUndefined(xmlData, i);
        if (!startChar.equals("\"") && !startChar.equals("'")) {
            throw JsError.error("Expected quoted string, found \"" + startChar + "\"");
        }
        char quote = startChar.charAt(0);
        i++;
        int valueStart = i;
        while (i < xmlData.length() && xmlData.charAt(i) != quote) {
            i++;
        }
        value[0] = xmlData.substring(valueStart, i);
        if (!charIs(xmlData, i, quote)) {
            throw JsError.error("Unterminated " + type + " value");
        }
        return i + 1;
    }

    private static int readElementExp(String xmlData, int start) {
        int i = skipWhitespace(xmlData, start);
        int nameStart = i;
        while (i < xmlData.length() && !JsNumber.isWhiteSpace(xmlData.charAt(i))) {
            i++;
        }
        String elementName = xmlData.substring(nameStart, i);
        if (!XmlNaming.qName(elementName)) {
            throw JsError.error("Invalid element name: \"" + elementName + "\"");
        }
        i = skipWhitespace(xmlData, i);
        if (charIs(xmlData, i, 'E') && hasSeq(xmlData, "MPTY", i)) {
            i += 4;
        } else if (charIs(xmlData, i, 'A') && hasSeq(xmlData, "NY", i)) {
            i += 2;
        } else if (charIs(xmlData, i, '(')) {
            i++;
            while (i < xmlData.length() && xmlData.charAt(i) != ')') {
                i++;
            }
            if (!charIs(xmlData, i, ')')) {
                throw JsError.error("Unterminated content model");
            }
        } else {
            throw JsError.error("Invalid Element Expression, found \"" + charOrUndefined(xmlData, i) + "\"");
        }
        return i;
    }
}
