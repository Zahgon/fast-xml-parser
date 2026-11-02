// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.function.UnaryOperator;

/** {@code JSON.parse} with V8's error messages, and {@code JSON.stringify}. */
public final class Json {
    private static final int CONTEXT = 10;

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    /**
     * {@code JSON.parse(text)}.
     *
     * @param text the JSON text
     * @return the value: objects are {@link JsObject}, arrays {@link JsArray}, numbers {@link Double}
     * @throws JsError a SyntaxError with V8's message when the text is not JSON
     */
    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhiteSpace();
        Object value = parser.parseValue();
        parser.skipWhiteSpace();
        if (parser.pos < text.length()) {
            throw parser.at("Unexpected non-whitespace character after JSON", parser.pos);
        }
        return value;
    }

    private JsError located(String message, int at) {
        return at(message + " in JSON", at);
    }

    private void skipWhiteSpace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    // MESSAGE at position N (line L column C), lines counted at \n, \r and \r\n.
    private JsError at(String message, int at) {
        int line = 1;
        int lastBreak = 0;
        for (int i = 0; i < at; i++) {
            if (src.charAt(i) == '\r' && i < at - 1 && src.charAt(i + 1) == '\n') {
                i++;
            }
            if (src.charAt(i) == '\r' || src.charAt(i) == '\n') {
                line++;
                lastBreak = i + 1;
            }
        }
        return JsError.syntaxError(message + " at position " + at + " (line " + line + " column "
                + (1 + at - lastBreak) + ")");
    }

    // V8's message for an unexpected token at a position, chosen by the kind of token found there.
    private JsError unexpected(int at) {
        if (at >= src.length()) {
            return JsError.syntaxError("Unexpected end of JSON input");
        }
        char c = src.charAt(at);
        if (c == '"') {
            return located("Unexpected string", at);
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return located("Unexpected number", at);
        }
        switch (src) {
            case "[object Object]", "undefined", "Infinity", "NaN":
                return JsError.syntaxError("\"" + src + "\" is not valid JSON");
            default:
                break;
        }
        String token = "Unexpected token '" + c + "', ";
        int length = src.length();
        if (length < CONTEXT * 2 + 1) {
            return JsError.syntaxError(token + "\"" + src + "\" is not valid JSON");
        }
        if (at < CONTEXT) {
            return JsError.syntaxError(token + "\"" + src.substring(0, at + CONTEXT) + "\"... is not valid JSON");
        }
        if (at < length - CONTEXT) {
            return JsError.syntaxError(token + "...\"" + src.substring(at - CONTEXT, at + CONTEXT)
                    + "\"... is not valid JSON");
        }
        return JsError.syntaxError(token + "...\"" + src.substring(at - CONTEXT) + "\" is not valid JSON");
    }

    private Object parseValue() {
        if (pos >= src.length()) {
            throw unexpected(pos);
        }
        char c = src.charAt(pos);
        switch (c) {
            case '"':
                return parseString();
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case 't':
                literal("true");
                return Boolean.TRUE;
            case 'f':
                literal("false");
                return Boolean.FALSE;
            case 'n':
                literal("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw unexpected(pos);
        }
    }

    private void literal(String word) {
        for (int i = 1; i < word.length(); i++) {
            int at = pos + i;
            if (at >= src.length() || src.charAt(at) != word.charAt(i)) {
                throw unexpected(at);
            }
        }
        pos += word.length();
    }

    private JsObject parseObject() {
        JsObject object = new JsObject();
        pos++;
        skipWhiteSpace();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return object;
        }
        if (pos >= src.length() || src.charAt(pos) != '"') {
            throw located("Expected property name or '}'", pos);
        }
        while (true) {
            String key = parseString();
            skipWhiteSpace();
            if (pos >= src.length() || src.charAt(pos) != ':') {
                throw located("Expected ':' after property name", pos);
            }
            pos++;
            skipWhiteSpace();
            object.defineOwn(key, parseValue());
            skipWhiteSpace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                skipWhiteSpace();
                if (pos >= src.length() || src.charAt(pos) != '"') {
                    throw located("Expected double-quoted property name", pos);
                }
            } else if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return object;
            } else {
                throw located("Expected ',' or '}' after property value", pos);
            }
        }
    }

    private JsArray parseArray() {
        JsArray array = new JsArray();
        pos++;
        skipWhiteSpace();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhiteSpace();
            array.push(parseValue());
            skipWhiteSpace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
            } else if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return array;
            } else {
                throw located("Expected ',' or ']' after array element", pos);
            }
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean digitAt(int at) {
        return at < src.length() && isDigit(src.charAt(at));
    }

    private Double parseNumber() {
        int start = pos;
        if (src.charAt(pos) == '-') {
            pos++;
            if (!digitAt(pos)) {
                throw located("No number after minus sign", pos);
            }
        }
        if (src.charAt(pos) == '0') {
            pos++;
            if (digitAt(pos)) {
                throw unexpected(pos);
            }
        } else {
            while (digitAt(pos)) {
                pos++;
            }
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            if (!digitAt(pos)) {
                throw located("Unterminated fractional number", pos);
            }
            while (digitAt(pos)) {
                pos++;
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            if (!digitAt(pos)) {
                throw located("Exponent part is missing a number", pos);
            }
            while (digitAt(pos)) {
                pos++;
            }
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    private String parseString() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= src.length()) {
                throw located("Unterminated string", pos);
            }
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                return out.toString();
            }
            if (c < 0x20) {
                throw located("Bad control character in string literal", pos);
            }
            if (c != '\\') {
                out.append(c);
                pos++;
                continue;
            }
            pos++;
            if (pos >= src.length()) {
                throw unexpected(pos);
            }
            char escape = src.charAt(pos);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    int code = 0;
                    for (int i = 1; i <= 4; i++) {
                        int digit = pos + i < src.length() ? Character.digit(src.charAt(pos + i), 16) : -1;
                        if (digit < 0 || src.charAt(pos + i) > 'f') {
                            throw located("Bad Unicode escape", pos + i);
                        }
                        code = code * 16 + digit;
                    }
                    out.append((char) code);
                    pos += 4;
                }
                default -> {
                    if (escape > 0xFF) {
                        throw unexpected(pos);
                    }
                    throw located("Bad escaped character", pos);
                }
            }
            pos++;
        }
    }

    /**
     * {@code JSON.stringify(value)}.
     *
     * @param value the value
     * @return the JSON text, or null for a value JSON.stringify answers undefined for
     */
    public static String stringify(Object value) {
        return stringify(value, "", UnaryOperator.identity());
    }

    /**
     * {@code JSON.stringify(value, replacer, gap)} with a replacer that maps every value before it is written.
     *
     * @param value the value
     * @param gap the indentation of each level ("" for none)
     * @param replacer maps each value (the root included) before it is serialised
     * @return the JSON text, or null for a value JSON.stringify answers undefined for
     */
    public static String stringify(Object value, String gap, UnaryOperator<Object> replacer) {
        StringBuilder out = new StringBuilder();
        return write(out, replacer.apply(value), gap, "", replacer) ? out.toString() : null;
    }

    private static boolean write(StringBuilder out, Object value, String gap, String indent,
            UnaryOperator<Object> replacer) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Boolean b) {
            out.append(b ? "true" : "false");
        } else if (value instanceof String s) {
            quote(out, s);
        } else if (value instanceof Double d) {
            out.append(d.isNaN() || d.isInfinite() ? "null" : JsNumber.toString(d));
        } else if (value == Undefined.INSTANCE || value instanceof JsFunction) {
            return false;
        } else if (value instanceof JsArray array) {
            writeArray(out, array, gap, indent, replacer);
        } else if (value instanceof JsObject object) {
            writeObject(out, object, gap, indent, replacer);
        } else {
            out.append("{}");
        }
        return true;
    }

    private static void writeArray(StringBuilder out, JsArray array, String gap, String indent,
            UnaryOperator<Object> replacer) {
        if (array.size() == 0) {
            out.append("[]");
            return;
        }
        String inner = indent + gap;
        out.append('[');
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            if (!gap.isEmpty()) {
                out.append('\n').append(inner);
            }
            if (!write(out, replacer.apply(array.get(i)), gap, inner, replacer)) {
                out.append("null");
            }
        }
        if (!gap.isEmpty()) {
            out.append('\n').append(indent);
        }
        out.append(']');
    }

    private static void writeObject(StringBuilder out, JsObject object, String gap, String indent,
            UnaryOperator<Object> replacer) {
        String inner = indent + gap;
        boolean first = true;
        out.append('{');
        for (String key : object.keys()) {
            Object member = replacer.apply(object.get(key));
            if (member == Undefined.INSTANCE || member instanceof JsFunction) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            if (!gap.isEmpty()) {
                out.append('\n').append(inner);
            }
            quote(out, key);
            out.append(gap.isEmpty() ? ":" : ": ");
            write(out, member, gap, inner, replacer);
        }
        if (!first && !gap.isEmpty()) {
            out.append('\n').append(indent);
        }
        out.append('}');
    }

    /**
     * Appends a string as JSON.stringify quotes it (lone surrogates escaped).
     *
     * @param out the output
     * @param s the string
     */
    public static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c) && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1))) {
                        out.append(c).append(s.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
