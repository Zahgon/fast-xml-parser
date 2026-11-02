// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** JavaScript number conversions: {@code Number::toString}, {@code ToNumber} on strings and {@code parseInt}. */
public final class JsNumber {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private JsNumber() {
    }

    /**
     * {@code Number.prototype.toString()} in radix 10: the shortest digits that round-trip, closest to the value.
     *
     * @param value the number
     * @return its JavaScript string form
     */
    public static String toString(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == 0) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value < 0) {
            return "-" + toString(-value);
        }
        BigDecimal shortest = shortest(value);
        String digits = shortest.unscaledValue().toString();
        int k = digits.length();
        int n = k - shortest.scale();
        StringBuilder out = new StringBuilder();
        if (k <= n && n <= 21) {
            out.append(digits).append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            int exponent = n - 1;
            out.append('e').append(exponent < 0 ? '-' : '+').append(Math.abs(exponent));
        }
        return out.toString();
    }

    // Java's Double.toString picks the shortest round-tripping decimal too, except that where a single digit would
    // do it also considers two digits; ECMAScript keeps the single digit.
    private static BigDecimal shortest(double value) {
        BigDecimal javaDigits = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        if (javaDigits.precision() != 2) {
            return javaDigits;
        }
        BigDecimal exact = new BigDecimal(value);
        BigDecimal best = null;
        for (RoundingMode mode : new RoundingMode[] {RoundingMode.FLOOR, RoundingMode.CEILING}) {
            BigDecimal candidate = exact.round(new MathContext(1, mode)).stripTrailingZeros();
            if (candidate.doubleValue() != value) {
                continue;
            }
            if (best == null) {
                best = candidate;
            } else {
                int closer = candidate.subtract(exact).abs().compareTo(best.subtract(exact).abs());
                boolean even = !candidate.unscaledValue().testBit(0);
                if (closer < 0 || (closer == 0 && even)) {
                    best = candidate;
                }
            }
        }
        return best == null ? javaDigits : best;
    }

    /**
     * Whether a character is JavaScript white space or a line terminator (what {@code trim} and {@code \s} remove).
     *
     * @param c the UTF-16 code unit
     * @return true for white space
     */
    public static boolean isWhiteSpace(char c) {
        return switch (c) {
            case '\t', '\n', '\u000B', '\f', '\r', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                    '　', '﻿' -> true;
            default -> c >= ' ' && c <= ' ';
        };
    }

    /**
     * {@code ToNumber} applied to a string ({@code Number(string)}).
     *
     * @param text the string
     * @return the number, NaN when the string is not a numeric literal
     */
    public static double stringToNumber(String text) {
        String s = JsString.trim(text);
        if (s.isEmpty()) {
            return 0;
        }
        switch (s) {
            case "Infinity", "+Infinity":
                return Double.POSITIVE_INFINITY;
            case "-Infinity":
                return Double.NEGATIVE_INFINITY;
            default:
                break;
        }
        if (s.length() > 2 && s.charAt(0) == '0') {
            int radix = switch (s.charAt(1)) {
                case 'x', 'X' -> 16;
                case 'o', 'O' -> 8;
                case 'b', 'B' -> 2;
                default -> 0;
            };
            if (radix != 0) {
                String digits = s.substring(2);
                for (int i = 0; i < digits.length(); i++) {
                    if (Character.digit(digits.charAt(i), radix) < 0 || digits.charAt(i) > 'z') {
                        return Double.NaN;
                    }
                }
                return new BigInteger(digits, radix).doubleValue();
            }
        }
        if (!DECIMAL.matcher(s).matches()) {
            return Double.NaN;
        }
        return Double.parseDouble(s);
    }

    /**
     * The global {@code parseInt(text, radix)} for radix 2 to 36.
     *
     * @param text the string
     * @param radix the radix
     * @return the integer read from the longest valid prefix, NaN when there is none
     */
    public static double parseInt(String text, int radix) {
        int i = 0;
        int length = text.length();
        while (i < length && isWhiteSpace(text.charAt(i))) {
            i++;
        }
        boolean negative = false;
        if (i < length && (text.charAt(i) == '-' || text.charAt(i) == '+')) {
            negative = text.charAt(i) == '-';
            i++;
        }
        if (radix == 16 && i + 1 < length && text.charAt(i) == '0'
                && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {
            i += 2;
        }
        int start = i;
        while (i < length && text.charAt(i) < 128 && Character.digit(text.charAt(i), radix) >= 0) {
            i++;
        }
        if (i == start) {
            return Double.NaN;
        }
        double magnitude = new BigInteger(text.substring(start, i), radix).doubleValue();
        return negative ? -magnitude : magnitude;
    }

    /**
     * The global {@code isFinite} applied to a value.
     *
     * @param value any value
     * @return whether {@code ToNumber(value)} is finite
     */
    public static boolean isFinite(Object value) {
        double number = Js.toNumber(value);
        return !Double.isNaN(number) && !Double.isInfinite(number);
    }
}
