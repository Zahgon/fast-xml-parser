// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/validator.js).

package io.github.naturalintelligence.fxp.validator;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsString;
import io.github.naturalintelligence.fxp.js.Json;
import io.github.naturalintelligence.fxp.naming.XmlNaming;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * {@code XMLValidator.validate(xmlData, options)}: {@code true} for well-formed XML, otherwise
 * {@code {err: {code, msg, line, col}}} for the first problem found.
 */
public final class XMLValidator {
    private final String xmlData;
    private final JsObject options;

    private XMLValidator(String xmlData, JsObject options) {
        this.xmlData = xmlData;
        this.options = options;
    }

    /** A validation failure found while scanning; carries the error object. */
    private static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient JsObject result;

        private Failure(JsObject result) {
            super(null, null, false, false);
            this.result = result;
        }
    }

    private record Tag(String tagName, int tagStartPos) {
    }

    /**
     * {@code validate(xmlData, options)}.
     *
     * @param xmlData the XML text
     * @param userOptions the validator options (allowBooleanAttributes, unpairedTags), any value
     * @return {@code Boolean.TRUE}, or the error object
     */
    public static Object validate(String xmlData, Object userOptions) {
        JsObject options = new JsObject();
        options.set("allowBooleanAttributes", false);
        options.set("unpairedTags", new JsArray());
        Js.assign(options, userOptions);
        String data = xmlData.startsWith("﻿") ? xmlData.substring(1) : xmlData;
        try {
            return new XMLValidator(data, options).run();
        } catch (Failure failure) {
            return failure.result;
        }
    }

    private boolean is(int index, char c) {
        return index >= 0 && index < xmlData.length() && xmlData.charAt(index) == c;
    }

    private static boolean isWhiteSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private Object run() {
        List<Tag> tags = new ArrayList<>();
        boolean tagFound = false;
        boolean reachedRoot = false;
        int length = xmlData.length();
        for (int i = 0; i < length; i++) {
            if (is(i, '<') && is(i + 1, '?')) {
                i = readPI(i + 2);
            } else if (is(i, '<')) {
                int tagStartPos = i;
                i++;
                if (is(i, '!')) {
                    i = readCommentAndCDATA(i);
                    continue;
                }
                boolean closingTag = false;
                if (is(i, '/')) {
                    closingTag = true;
                    i++;
                }
                StringBuilder name = new StringBuilder();
                for (; i < length && xmlData.charAt(i) != '>' && xmlData.charAt(i) != ' '
                        && xmlData.charAt(i) != '\t' && xmlData.charAt(i) != '\n' && xmlData.charAt(i) != '\r'; i++) {
                    name.append(xmlData.charAt(i));
                }
                String tagName = JsString.trim(name.toString());
                if (tagName.endsWith("/")) {
                    tagName = tagName.substring(0, tagName.length() - 1);
                    i--;
                }
                if (!XmlNaming.isName(tagName)) {
                    String msg = JsString.trim(tagName).isEmpty()
                            ? "Invalid space after '<'." : "Tag '" + tagName + "' is an invalid name.";
                    throw fail("InvalidTag", msg, lineNumberFor(i));
                }
                AttributeString result = readAttributeStr(i);
                if (result == null) {
                    throw fail("InvalidAttr", "Attributes for '" + tagName + "' have open quote.", lineNumberFor(i));
                }
                String attrStr = result.value();
                i = result.index();
                if (attrStr.endsWith("/")) {
                    int attrStrStart = i - attrStr.length();
                    attrStr = attrStr.substring(0, attrStr.length() - 1);
                    validateAttributeString(attrStr, attrStrStart);
                    tagFound = true;
                } else if (closingTag) {
                    if (!result.tagClosed()) {
                        throw fail("InvalidTag", "Closing tag '" + tagName + "' doesn't have proper closing.",
                                lineNumberFor(i));
                    } else if (!JsString.trim(attrStr).isEmpty()) {
                        throw fail("InvalidTag", "Closing tag '" + tagName
                                + "' can't have attributes or invalid starting.", lineNumberFor(tagStartPos));
                    } else if (tags.isEmpty()) {
                        throw fail("InvalidTag", "Closing tag '" + tagName + "' has not been opened.",
                                lineNumberFor(tagStartPos));
                    } else {
                        Tag otg = tags.remove(tags.size() - 1);
                        if (!tagName.equals(otg.tagName())) {
                            JsObject openPos = lineNumberFor(otg.tagStartPos());
                            throw fail("InvalidTag", "Expected closing tag '" + otg.tagName() + "' (opened in line "
                                    + Js.toStr(openPos.get("line")) + ", col " + Js.toStr(openPos.get("col"))
                                    + ") instead of closing tag '" + tagName + "'.", lineNumberFor(tagStartPos));
                        }
                        if (tags.isEmpty()) {
                            reachedRoot = true;
                        }
                    }
                } else {
                    validateAttributeString(attrStr, i - attrStr.length());
                    if (reachedRoot) {
                        throw fail("InvalidXml", "Multiple possible root nodes found.", lineNumberFor(i));
                    } else if (!isUnpaired(tagName)) {
                        tags.add(new Tag(tagName, tagStartPos));
                    }
                    tagFound = true;
                }
                for (i++; i < length; i++) {
                    if (is(i, '<')) {
                        if (is(i + 1, '!')) {
                            i++;
                            i = readCommentAndCDATA(i);
                            continue;
                        } else if (is(i + 1, '?')) {
                            i = readPI(++i);
                        } else {
                            break;
                        }
                    } else if (is(i, '&')) {
                        int afterAmp = validateAmpersand(i);
                        if (afterAmp == -1) {
                            throw fail("InvalidChar", "char '&' is not expected.", lineNumberFor(i));
                        }
                        i = afterAmp;
                    } else if (reachedRoot && !isWhiteSpace(xmlData.charAt(i))) {
                        throw fail("InvalidXml", "Extra text at the end", lineNumberFor(i));
                    }
                }
                if (is(i, '<')) {
                    i--;
                }
            } else {
                if (isWhiteSpace(xmlData.charAt(i))) {
                    continue;
                }
                throw fail("InvalidChar", "char '" + xmlData.charAt(i) + "' is not expected.", lineNumberFor(i));
            }
        }
        if (!tagFound) {
            throw fail("InvalidXml", "Start tag expected.", null);
        } else if (tags.size() == 1) {
            throw fail("InvalidTag", "Unclosed tag '" + tags.get(0).tagName() + "'.",
                    lineNumberFor(tags.get(0).tagStartPos()));
        } else if (!tags.isEmpty()) {
            JsArray names = new JsArray();
            tags.forEach(tag -> names.push(tag.tagName()));
            String list = Json.stringify(names, "    ", UnaryOperator.identity()).replaceAll("\\r?\\n", "");
            JsObject position = new JsObject();
            position.set("line", 1.0);
            position.set("col", 1.0);
            throw fail("InvalidXml", "Invalid '" + list + "' found.", position);
        }
        return Boolean.TRUE;
    }

    private boolean isUnpaired(String tagName) {
        Object unpairedTags = options.get("unpairedTags");
        if (unpairedTags instanceof String s) {
            return s.contains(tagName);
        }
        if (unpairedTags instanceof JsArray array) {
            return array.items().stream().anyMatch(tag -> Js.strictEquals(tag, tagName));
        }
        Js.call(Js.get(unpairedTags, "indexOf"), "options.unpairedTags.indexOf", tagName);
        return false;
    }

    // getErrorObject: line is lineNumber.line || lineNumber, col is lineNumber.col.
    private static Failure fail(String code, String message, JsObject lineNumber) {
        JsObject err = new JsObject();
        err.set("code", code);
        err.set("msg", message);
        err.set("line", lineNumber == null ? 1.0 : lineNumber.get("line"));
        err.set("col", lineNumber == null ? Js.UNDEFINED : lineNumber.get("col"));
        JsObject result = new JsObject();
        result.set("err", err);
        return new Failure(result);
    }

    private JsObject lineNumberFor(int index) {
        String before = xmlData.substring(0, Math.min(Math.max(index, 0), xmlData.length()));
        String[] lines = before.split("\\r?\\n", -1);
        JsObject position = new JsObject();
        position.set("line", (double) lines.length);
        position.set("col", (double) (lines[lines.length - 1].length() + 1));
        return position;
    }

    private int readPI(int start) {
        int i = start;
        for (; i < xmlData.length(); i++) {
            if (is(i, '?') || is(i, ' ')) {
                String tagname = xmlData.substring(start, i);
                if (i > 5 && tagname.equals("xml")) {
                    throw fail("InvalidXml", "XML declaration allowed only at the start of the document.",
                            lineNumberFor(i));
                } else if (is(i, '?') && is(i + 1, '>')) {
                    i++;
                    break;
                }
            }
        }
        return i;
    }

    private int readCommentAndCDATA(int start) {
        int i = start;
        int length = xmlData.length();
        if (length > i + 5 && is(i + 1, '-') && is(i + 2, '-')) {
            for (i += 3; i < length; i++) {
                if (is(i, '-') && is(i + 1, '-') && is(i + 2, '>')) {
                    i += 2;
                    break;
                }
            }
        } else if (length > i + 8 && xmlData.startsWith("DOCTYPE", i + 1)) {
            int angleBracketsCount = 1;
            for (i += 8; i < length; i++) {
                if (is(i, '<')) {
                    angleBracketsCount++;
                } else if (is(i, '>')) {
                    angleBracketsCount--;
                    if (angleBracketsCount == 0) {
                        break;
                    }
                }
            }
        } else if (length > i + 9 && xmlData.startsWith("[CDATA[", i + 1)) {
            for (i += 8; i < length; i++) {
                if (is(i, ']') && is(i + 1, ']') && is(i + 2, '>')) {
                    i += 2;
                    break;
                }
            }
        }
        return i;
    }

    private record AttributeString(String value, int index, boolean tagClosed) {
    }

    private AttributeString readAttributeStr(int start) {
        StringBuilder attrStr = new StringBuilder();
        char startChar = 0;
        boolean tagClosed = false;
        int i = start;
        for (; i < xmlData.length(); i++) {
            char c = xmlData.charAt(i);
            if (c == '"' || c == '\'') {
                if (startChar == 0) {
                    startChar = c;
                } else if (startChar == c) {
                    startChar = 0;
                }
            } else if (c == '>' && startChar == 0) {
                tagClosed = true;
                break;
            }
            attrStr.append(c);
        }
        if (startChar != 0) {
            return null;
        }
        return new AttributeString(attrStr.toString(), i, tagClosed);
    }

    private record AttrToken(int startIndex, String leadingWs, String name, String equals, String quote) {
    }

    private static List<AttrToken> scanAttributeTokens(String attrStr) {
        List<AttrToken> tokens = new ArrayList<>();
        int len = attrStr.length();
        int i = 0;
        while (i < len) {
            int tokenStart = i;
            while (i < len && isWhiteSpace(attrStr.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (attrStr.charAt(i) == '=') {
                i = tokenStart + 1;
                continue;
            }
            String leadingWs = attrStr.substring(tokenStart, i);
            int nameStart = i;
            while (i < len && !isWhiteSpace(attrStr.charAt(i)) && attrStr.charAt(i) != '=') {
                i++;
            }
            String name = attrStr.substring(nameStart, i);
            String equalsGroup = null;
            int j = i;
            while (j < len && isWhiteSpace(attrStr.charAt(j))) {
                j++;
            }
            if (j < len && attrStr.charAt(j) == '=') {
                equalsGroup = attrStr.substring(i, j + 1);
                i = j + 1;
            }
            String quote = null;
            int k = i;
            while (k < len && isWhiteSpace(attrStr.charAt(k))) {
                k++;
            }
            if (k < len && (attrStr.charAt(k) == '"' || attrStr.charAt(k) == '\'')) {
                int closeIdx = attrStr.indexOf(attrStr.charAt(k), k + 1);
                if (closeIdx != -1) {
                    quote = String.valueOf(attrStr.charAt(k));
                    i = closeIdx + 1;
                }
            }
            tokens.add(new AttrToken(tokenStart, leadingWs, name, equalsGroup, quote));
        }
        return tokens;
    }

    // Validates an attribute string starting at index offset of the document; throws on the first problem.
    private void validateAttributeString(String attrStr, int offset) {
        Set<String> attrNames = new HashSet<>();
        for (AttrToken token : scanAttributeTokens(attrStr)) {
            int position = token.startIndex() + token.leadingWs().length();
            String code = null;
            String msg = null;
            if (token.leadingWs().isEmpty()) {
                code = "InvalidAttr";
                msg = "Attribute '" + token.name() + "' has no space in starting.";
            } else if (token.equals() != null && token.quote() == null) {
                code = "InvalidAttr";
                msg = "Attribute '" + token.name() + "' is without value.";
            } else if (token.equals() == null && !Js.truthy(options.get("allowBooleanAttributes"))) {
                code = "InvalidAttr";
                msg = "boolean attribute '" + token.name() + "' is not allowed.";
            } else if (!XmlNaming.isName(token.name())) {
                code = "InvalidAttr";
                msg = "Attribute '" + token.name() + "' is an invalid name.";
            } else if (!attrNames.add(token.name())) {
                code = "InvalidAttr";
                msg = "Attribute '" + token.name() + "' is repeated.";
            }
            if (code != null) {
                throw fail(code, msg, lineNumberFor(offset + position));
            }
        }
    }

    private int validateNumberAmpersand(int start) {
        int i = start;
        boolean hex = false;
        if (is(i, 'x')) {
            i++;
            hex = true;
        }
        for (; i < xmlData.length(); i++) {
            char c = xmlData.charAt(i);
            if (c == ';') {
                return i;
            }
            boolean valid = (c >= '0' && c <= '9') || (hex && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')));
            if (!valid) {
                break;
            }
        }
        return -1;
    }

    private int validateAmpersand(int start) {
        int i = start + 1;
        if (is(i, ';')) {
            return -1;
        }
        if (is(i, '#')) {
            return validateNumberAmpersand(i + 1);
        }
        int count = 0;
        for (; i < xmlData.length(); i++, count++) {
            char c = xmlData.charAt(i);
            boolean word = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (word && count < 20) {
                continue;
            }
            if (c == ';') {
                break;
            }
            return -1;
        }
        return i;
    }
}
