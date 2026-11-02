// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Node.js's {@code util.format(value)} for one argument, as {@code console.log} prints it: strings as they are, other
 * values through {@code util.inspect} with its defaults (depth 2, {@code compact: 3}, {@code breakLength: 80}).
 */
public final class Inspect {
    private static final int DEPTH = 2;
    private static final int COMPACT = 3;
    private static final int BREAK_LENGTH = 80;
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*");

    private int indentationLvl;
    private int currentDepth;

    private Inspect() {
    }

    /**
     * {@code util.format(value)}.
     *
     * @param value the value
     * @return the text console.log writes before its newline
     */
    public static String format(Object value) {
        if (value instanceof String s) {
            return s;
        }
        return new Inspect().formatValue(value, 0);
    }

    private String formatValue(Object value, int recurseTimes) {
        if (value instanceof JsObject object) {
            return formatObject(object, recurseTimes);
        }
        if (value instanceof JsArray array) {
            return formatArray(array, recurseTimes);
        }
        return formatPrimitive(value);
    }

    private String formatPrimitive(Object value) {
        if (value instanceof String s) {
            return quote(s);
        }
        if (value instanceof Double d) {
            return d == 0 && 1 / d < 0 ? "-0" : JsNumber.toString(d);
        }
        return Js.toStr(value);
    }

    private String formatObject(JsObject object, int recurseTimes) {
        List<String> keys = object.keys();
        if (keys.isEmpty()) {
            return "{}";
        }
        if (recurseTimes > DEPTH) {
            return "[Object]";
        }
        int depth = recurseTimes + 1;
        currentDepth = depth;
        List<String> output = new ArrayList<>();
        for (String key : keys) {
            indentationLvl += 2;
            String str = formatValue(object.get(key), depth);
            indentationLvl -= 2;
            String name = "__proto__".equals(key) ? "['__proto__']"
                    : IDENTIFIER.matcher(key).matches() ? key : quote(key);
            output.add(name + ": " + str);
        }
        return reduceToSingleString(output, "{", "}", depth);
    }

    private String formatArray(JsArray array, int recurseTimes) {
        if (array.size() == 0) {
            return "[]";
        }
        if (recurseTimes > DEPTH) {
            return "[Array]";
        }
        int depth = recurseTimes + 1;
        currentDepth = depth;
        List<String> output = new ArrayList<>();
        for (Object element : array) {
            indentationLvl += 2;
            output.add(formatValue(element, depth));
            indentationLvl -= 2;
        }
        return reduceToSingleString(output, "[", "]", depth);
    }

    private String reduceToSingleString(List<String> output, String open, String close, int recurseTimes) {
        if (currentDepth - recurseTimes < COMPACT) {
            int start = output.size() + indentationLvl + open.length() + 10;
            if (isBelowBreakLength(output, start)) {
                String joined = String.join(", ", output);
                if (!joined.contains("\n")) {
                    return open + " " + joined + " " + close;
                }
            }
        }
        String indentation = "\n" + " ".repeat(indentationLvl);
        return open + indentation + "  " + String.join("," + indentation + "  ", output) + indentation + close;
    }

    private static boolean isBelowBreakLength(List<String> output, int start) {
        int totalLength = output.size() + start;
        if (totalLength + output.size() > BREAK_LENGTH) {
            return false;
        }
        for (String entry : output) {
            totalLength += entry.length();
            if (totalLength > BREAK_LENGTH) {
                return false;
            }
        }
        return true;
    }

    // strEscape: single quotes unless the string has one; then double quotes, else backticks, else escaped quotes.
    private static String quote(String s) {
        char quote = '\'';
        if (s.indexOf('\'') >= 0) {
            if (s.indexOf('"') < 0) {
                quote = '"';
            } else if (s.indexOf('`') < 0 && !s.contains("${")) {
                quote = '`';
            }
        }
        StringBuilder out = new StringBuilder().append(quote);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '\'' && quote == '\'') || c == '\\' || c < 32 || (c > 126 && c < 160)) {
                out.append(escape(c));
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                out.append(c).append(s.charAt(++i));
            } else if (Character.isSurrogate(c)) {
                out.append("\\u").append(Integer.toHexString(c));
            } else {
                out.append(c);
            }
        }
        return out.append(quote).toString();
    }

    private static String escape(char c) {
        return switch (c) {
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\t' -> "\\t";
            case '\n' -> "\\n";
            case '\f' -> "\\f";
            case '\r' -> "\\r";
            default -> String.format("\\x%02X", (int) c);
        };
    }
}
