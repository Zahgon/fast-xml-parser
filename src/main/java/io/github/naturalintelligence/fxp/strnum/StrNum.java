// SPDX-License-Identifier: MIT
// Copyright (c) 2021 Natural Intelligence (github.com/NaturalIntelligence/strnum)
// Ported to Java from strnum 2.4.2 (strnum.js), the number parser of fast-xml-parser.

package io.github.naturalintelligence.fxp.strnum;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsNumber;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsRegExp;
import io.github.naturalintelligence.fxp.js.JsString;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code toNumber(str, options)}: turns numeric strings into numbers under the strnum options. */
public final class StrNum {
    private static final Pattern HEX = Pattern.compile("[-+]?0x[a-fA-F0-9]+");
    private static final Pattern BIN = Pattern.compile("0b[01]+");
    private static final Pattern OCT = Pattern.compile("0o[0-7]+");
    private static final Pattern NUM = Pattern.compile("([\\-+])?(0*)([0-9]*(\\.[0-9]*)?)");
    private static final Pattern E_NOTATION = Pattern.compile("([-+])?(0*)([0-9]*(\\.[0-9]*)?[eE][-+]?[0-9]+)");

    private StrNum() {
    }

    private static JsObject consider() {
        JsObject consider = new JsObject();
        consider.set("hex", true);
        consider.set("binary", false);
        consider.set("octal", false);
        consider.set("leadingZeros", true);
        consider.set("decimalPoint", "\\.");
        consider.set("eNotation", true);
        consider.set("infinity", "original");
        consider.set("unicode", false);
        return consider;
    }

    /**
     * {@code toNumber(str, options)}.
     *
     * @param str the value; anything but a non-empty string is returned as it is
     * @param userOptions the strnum options, merged over the defaults with {@code Object.assign}
     * @return a number, the original value, or what the infinity option asks for
     */
    public static Object toNumber(Object str, Object userOptions) {
        JsObject options = consider();
        Js.assign(options, userOptions);
        if (!Js.truthy(str) || !(str instanceof String original)) {
            return str;
        }
        String trimmedStr = JsString.trim(original);
        Object skipLike = options.get("skipLike");
        if (trimmedStr.isEmpty()) {
            return str;
        } else if (skipLike != Js.UNDEFINED && test(skipLike, trimmedStr)) {
            return str;
        } else if (trimmedStr.equals("0")) {
            return 0.0;
        }
        if (Js.truthy(options.get("unicode"))) {
            trimmedStr = AnyNum.anynum(trimmedStr);
            if (trimmedStr.equals("0")) {
                return 0.0;
            }
        }
        if (Js.truthy(options.get("hex")) && HEX.matcher(trimmedStr).matches()) {
            return parseInt(trimmedStr, 16);
        } else if (Js.truthy(options.get("binary")) && BIN.matcher(trimmedStr).matches()) {
            return parseInt(trimmedStr, 2);
        } else if (Js.truthy(options.get("octal")) && OCT.matcher(trimmedStr).matches()) {
            return parseInt(trimmedStr, 8);
        } else if (!JsNumber.isFinite(trimmedStr)) {
            return handleInfinity(original, JsNumber.stringToNumber(trimmedStr), options);
        } else if (trimmedStr.indexOf('e') >= 0 || trimmedStr.indexOf('E') >= 0) {
            return resolveENotation(original, trimmedStr, options);
        }
        Matcher match = NUM.matcher(trimmedStr);
        if (!match.matches()) {
            return str;
        }
        String sign = match.group(1) == null ? "" : match.group(1);
        String leadingZeros = match.group(2);
        String numTrimmedByZeros = trimZeros(match.group(3));
        boolean decimalAdjacentToLeadingZeros = Js.strictEquals(
                JsString.charAt(original, leadingZeros.length() + (sign.isEmpty() ? 0 : 1)), ".");
        if (!Js.truthy(options.get("leadingZeros"))
                && (leadingZeros.length() > 1 || (leadingZeros.length() == 1 && !decimalAdjacentToLeadingZeros))) {
            return str;
        }
        double num = JsNumber.stringToNumber(trimmedStr);
        String parsedStr = JsNumber.toString(num);
        if (num == 0) {
            return num;
        }
        if (parsedStr.indexOf('e') >= 0 || parsedStr.indexOf('E') >= 0) {
            return Js.truthy(options.get("eNotation")) ? (Object) num : str;
        } else if (trimmedStr.indexOf('.') >= 0) {
            if (parsedStr.equals("0") || parsedStr.equals(numTrimmedByZeros)
                    || parsedStr.equals(sign + numTrimmedByZeros)) {
                return num;
            }
            return str;
        }
        if (!leadingZeros.isEmpty()) {
            return numTrimmedByZeros.equals(parsedStr) || (sign + numTrimmedByZeros).equals(parsedStr) ? (Object) num
                    : str;
        }
        return trimmedStr.equals(parsedStr) || trimmedStr.equals(sign + parsedStr) ? (Object) num : str;
    }

    private static boolean test(Object skipLike, String text) {
        Object method = Js.get(skipLike, "test");
        if (skipLike instanceof JsRegExp regexp) {
            return regexp.test(text);
        }
        return Js.truthy(Js.call(method, "options.skipLike.test", text));
    }

    private static Object resolveENotation(String str, String trimmedStr, JsObject options) {
        if (!Js.truthy(options.get("eNotation"))) {
            return str;
        }
        Matcher notation = E_NOTATION.matcher(trimmedStr);
        if (!notation.matches()) {
            return str;
        }
        String sign = notation.group(1) == null ? "" : notation.group(1);
        String body = notation.group(3);
        String eChar = body.indexOf('e') < 0 ? "E" : "e";
        String leadingZeros = notation.group(2);
        boolean eAdjacentToLeadingZeros = Js.strictEquals(
                JsString.charAt(str, leadingZeros.length() + (sign.isEmpty() ? 0 : 1)), eChar);
        if (leadingZeros.length() > 1 && eAdjacentToLeadingZeros) {
            return str;
        } else if (leadingZeros.length() == 1 && (body.startsWith("." + eChar) || body.startsWith(eChar))) {
            return JsNumber.stringToNumber(trimmedStr);
        } else if (!leadingZeros.isEmpty()) {
            if (Js.truthy(options.get("leadingZeros")) && !eAdjacentToLeadingZeros) {
                return JsNumber.stringToNumber(sign + body);
            }
            return str;
        }
        return JsNumber.stringToNumber(trimmedStr);
    }

    private static String trimZeros(String numStr) {
        if (numStr != null && numStr.indexOf('.') >= 0) {
            int end = numStr.length();
            while (end > 0 && numStr.charAt(end - 1) == '0') {
                end--;
            }
            String trimmed = numStr.substring(0, end);
            if (trimmed.equals(".")) {
                return "0";
            } else if (trimmed.startsWith(".")) {
                return "0" + trimmed;
            } else if (trimmed.endsWith(".")) {
                return trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
        return numStr;
    }

    private static double parseInt(String numStr, int base) {
        String str = JsString.trim(numStr);
        String digits = base == 2 || base == 8 ? str.substring(2) : numStr;
        return JsNumber.parseInt(digits, base);
    }

    private static Object handleInfinity(String str, double num, JsObject options) {
        boolean isPositive = num == Double.POSITIVE_INFINITY;
        Object infinity = options.get("infinity");
        String lower = infinity instanceof String s ? JsString.toLowerCase(s)
                : Js.toStr(Js.call(Js.get(infinity, "toLowerCase"), "options.infinity.toLowerCase"));
        return switch (lower) {
            case "null" -> null;
            case "infinity" -> num;
            case "string" -> isPositive ? "Infinity" : "-Infinity";
            default -> str;
        };
    }
}
