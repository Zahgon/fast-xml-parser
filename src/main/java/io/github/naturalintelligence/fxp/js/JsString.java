// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.Locale;

/** JavaScript string methods whose behaviour differs from Java's own. */
public final class JsString {
    private JsString() {
    }

    /**
     * {@code String.prototype.trim}: removes JavaScript white space and line terminators from both ends.
     *
     * @param s the string
     * @return the trimmed string
     */
    public static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && JsNumber.isWhiteSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && JsNumber.isWhiteSpace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    /**
     * {@code String.prototype.trimStart}.
     *
     * @param s the string
     * @return the string without leading white space
     */
    public static String trimStart(String s) {
        int start = 0;
        while (start < s.length() && JsNumber.isWhiteSpace(s.charAt(start))) {
            start++;
        }
        return s.substring(start);
    }

    /**
     * {@code String.prototype.toLowerCase}: Unicode default case conversion.
     *
     * @param s the string
     * @return the lower-cased string
     */
    public static String toLowerCase(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * {@code String.prototype.toUpperCase}: Unicode default case conversion.
     *
     * @param s the string
     * @return the upper-cased string
     */
    public static String toUpperCase(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /**
     * {@code String.prototype.search(/\s/)}: the index of the first JavaScript white-space character.
     *
     * @param s the string
     * @return the index, -1 when there is none
     */
    public static int searchWhiteSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (JsNumber.isWhiteSpace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * {@code String.prototype.substr(start, length)} for a non-negative start.
     *
     * @param s the string
     * @param start the start index
     * @param length the number of code units
     * @return the substring
     */
    public static String substr(String s, int start, int length) {
        int from = Math.min(Math.max(start, 0), s.length());
        int to = Math.min(from + Math.max(length, 0), s.length());
        return s.substring(from, to);
    }

    /**
     * {@code String.prototype.substr(start)} for a non-negative start.
     *
     * @param s the string
     * @param start the start index
     * @return the substring
     */
    public static String substr(String s, int start) {
        return s.substring(Math.min(Math.max(start, 0), s.length()));
    }

    /**
     * {@code String.prototype.substring(start, end)}: bounds clamped to the string and swapped when reversed.
     *
     * @param s the string
     * @param start one bound
     * @param end the other bound
     * @return the substring
     */
    public static String substring(String s, int start, int end) {
        int a = Math.min(Math.max(start, 0), s.length());
        int b = Math.min(Math.max(end, 0), s.length());
        return s.substring(Math.min(a, b), Math.max(a, b));
    }

    /**
     * {@code string[index]} for one code unit.
     *
     * @param s the string
     * @param index the index
     * @return the one-unit string, undefined out of range
     */
    public static Object charAt(String s, int index) {
        return index >= 0 && index < s.length() ? String.valueOf(s.charAt(index)) : Undefined.INSTANCE;
    }
}
