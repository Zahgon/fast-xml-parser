// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates a JavaScript regular expression (the Annex B dialect without the {@code u} and {@code v} flags) into an
 * equivalent {@link java.util.regex.Pattern} source, reporting V8's syntax errors. Every JavaScript capture group k
 * becomes the Java group {@code gk}, with an empty group {@code mk} at its start that tells a backreference whether
 * the group took part in the match (a JavaScript backreference to a group that did not matches the empty string).
 */
final class RegExpTranslator {
    private static final int MAX_CHAR = 0xFFFF;
    private static final int[][] DIGIT = {{'0', '9'}};
    private static final int[][] WORD = {{'0', '9'}, {'A', 'Z'}, {'_', '_'}, {'a', 'z'}};
    private static final int[][] SPACE = {{0x09, 0x0D}, {0x20, 0x20}, {0xA0, 0xA0}, {0x1680, 0x1680}, {0x2000, 0x200A},
        {0x2028, 0x2029}, {0x202F, 0x202F}, {0x205F, 0x205F}, {0x3000, 0x3000}, {0xFEFF, 0xFEFF}};
    private static final String WORD_CLASS = "[A-Za-z0-9_]";
    private static final String LINE_TERMINATORS = "[^\\n\\r\\x{2028}\\x{2029}]";

    private final String src;
    private final String flags;
    private final boolean multiline;
    private final boolean dotAll;
    private final StringBuilder out = new StringBuilder();
    private final List<String> groupNames = new ArrayList<>();
    private final List<String> referencedNames = new ArrayList<>();
    private int pos;
    private int captureCount;
    private int totalCaptures;
    private boolean hasNamedGroups;

    private RegExpTranslator(String src, String flags) {
        this.src = src;
        this.flags = flags;
        this.multiline = flags.indexOf('m') >= 0;
        this.dotAll = flags.indexOf('s') >= 0;
    }

    /** The result of a translation: the Java pattern source and the names of the capture groups, in order. */
    record Result(String javaSource, int captureCount, List<String> groupNames) {
    }

    static Result translate(String src, String flags) {
        RegExpTranslator translator = new RegExpTranslator(src, flags);
        translator.scanCaptures();
        translator.parseDisjunction();
        if (translator.pos < src.length()) {
            throw translator.error("Unmatched ')'");
        }
        for (String name : translator.referencedNames) {
            if (!translator.groupNames.contains(name)) {
                throw translator.error("Invalid named capture referenced");
            }
        }
        return new Result(translator.out.toString(), translator.captureCount, translator.groupNames);
    }

    private JsError error(String message) {
        return JsError.syntaxError("Invalid regular expression: /" + src + "/" + canonicalFlags(flags) + ": "
                + message);
    }

    /**
     * The flags in the order {@code RegExp.prototype.flags} writes them.
     *
     * @param flags valid flags in any order
     * @return the flags ordered as "dgimsuvy"
     */
    static String canonicalFlags(String flags) {
        StringBuilder out = new StringBuilder();
        for (char c : "dgimsuvy".toCharArray()) {
            if (flags.indexOf(c) >= 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    // Counts the capture groups the way V8 does before it resolves backreferences.
    private void scanCaptures() {
        boolean inClass = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\\') {
                i++;
            } else if (inClass) {
                inClass = c != ']';
            } else if (c == '[') {
                inClass = true;
            } else if (c == '(') {
                if (i + 1 < src.length() && src.charAt(i + 1) == '?') {
                    if (i + 2 < src.length() && src.charAt(i + 2) == '<' && i + 3 < src.length()
                            && src.charAt(i + 3) != '=' && src.charAt(i + 3) != '!') {
                        totalCaptures++;
                        hasNamedGroups = true;
                    }
                } else {
                    totalCaptures++;
                }
            }
        }
    }

    private boolean more() {
        return pos < src.length();
    }

    private char peek() {
        return src.charAt(pos);
    }

    private boolean at(int offset, char c) {
        return pos + offset < src.length() && src.charAt(pos + offset) == c;
    }

    private void parseDisjunction() {
        parseAlternative();
        while (more() && peek() == '|') {
            pos++;
            out.append('|');
            parseAlternative();
        }
    }

    private void parseAlternative() {
        while (more() && peek() != '|' && peek() != ')') {
            parseTerm();
        }
    }

    private void parseTerm() {
        char c = peek();
        switch (c) {
            case '^' -> {
                pos++;
                out.append(multiline ? "(?<!" + LINE_TERMINATORS + ")" : "^");
                rejectQuantifier();
                return;
            }
            case '$' -> {
                pos++;
                out.append(multiline ? "(?!" + LINE_TERMINATORS + ")" : "\\z");
                rejectQuantifier();
                return;
            }
            case '\\' -> {
                if (at(1, 'b') || at(1, 'B')) {
                    boolean boundary = src.charAt(pos + 1) == 'b';
                    pos += 2;
                    out.append(boundary
                            ? "(?:(?<=" + WORD_CLASS + ")(?!" + WORD_CLASS + ")|(?<!" + WORD_CLASS + ")(?=" + WORD_CLASS
                                    + "))"
                            : "(?:(?<=" + WORD_CLASS + ")(?=" + WORD_CLASS + ")|(?<!" + WORD_CLASS + ")(?!" + WORD_CLASS
                                    + "))");
                    rejectQuantifier();
                    return;
                }
                parseAtomEscape();
            }
            case '(' -> {
                if (parseGroup()) {
                    rejectQuantifier();
                    return;
                }
            }
            case '[' -> parseClass();
            case '*', '+', '?' -> throw error("Nothing to repeat");
            case '{' -> {
                if (quantifierLength() > 0) {
                    throw error("Nothing to repeat");
                }
                pos++;
                literal('{');
            }
            case '.' -> {
                pos++;
                out.append(dotAll ? "(?s:.)" : LINE_TERMINATORS);
            }
            default -> {
                pos++;
                literal(c);
            }
        }
        parseQuantifier();
    }

    private void rejectQuantifier() {
        if (more() && (peek() == '*' || peek() == '+' || peek() == '?' || (peek() == '{' && quantifierLength() > 0))) {
            throw error("Nothing to repeat");
        }
    }

    // The length of a {n}, {n,} or {n,m} quantifier at pos, 0 when the text there is not one.
    private int quantifierLength() {
        int i = pos + 1;
        int digits = i;
        while (i < src.length() && Character.isDigit(src.charAt(i)) && src.charAt(i) < 128) {
            i++;
        }
        if (i == digits) {
            return 0;
        }
        if (i < src.length() && src.charAt(i) == ',') {
            i++;
            while (i < src.length() && Character.isDigit(src.charAt(i)) && src.charAt(i) < 128) {
                i++;
            }
        }
        return i < src.length() && src.charAt(i) == '}' ? i + 1 - pos : 0;
    }

    private void parseQuantifier() {
        if (!more()) {
            return;
        }
        char c = peek();
        if (c == '*' || c == '+' || c == '?') {
            pos++;
            out.append(c);
        } else if (c == '{' && quantifierLength() > 0) {
            String body = src.substring(pos + 1, pos + quantifierLength() - 1);
            pos += quantifierLength();
            int comma = body.indexOf(',');
            long min = parseBound(comma < 0 ? body : body.substring(0, comma));
            long max = comma < 0 ? min : comma == body.length() - 1 ? -1 : parseBound(body.substring(comma + 1));
            if (max >= 0 && max < min) {
                throw error("numbers out of order in {} quantifier");
            }
            out.append('{').append(min).append(comma < 0 ? "" : ",").append(comma < 0 || max < 0 ? "" : max)
                    .append('}');
        } else {
            return;
        }
        if (more() && peek() == '?') {
            pos++;
            out.append('?');
        }
        rejectQuantifier();
    }

    private static long parseBound(String digits) {
        return digits.length() > 9 ? Integer.MAX_VALUE : Math.min(Long.parseLong(digits), Integer.MAX_VALUE);
    }

    // Returns true for a lookbehind, which takes no quantifier.
    private boolean parseGroup() {
        pos++;
        boolean lookbehind = false;
        if (more() && peek() == '?') {
            if (at(1, ':') || at(1, '=') || at(1, '!')) {
                out.append("(?").append(src.charAt(pos + 1));
                pos += 2;
            } else if (at(1, '<') && (at(2, '=') || at(2, '!'))) {
                out.append("(?<").append(src.charAt(pos + 2));
                pos += 3;
                lookbehind = true;
            } else if (at(1, '<')) {
                pos += 2;
                String name = parseGroupName();
                if (groupNames.contains(name)) {
                    throw error("Duplicate capture group name");
                }
                openCapture(name);
            } else {
                throw error("Invalid group");
            }
        } else {
            openCapture(null);
        }
        parseDisjunction();
        if (!more()) {
            throw error("Unterminated group");
        }
        pos++;
        out.append(')');
        return lookbehind;
    }

    private void openCapture(String name) {
        captureCount++;
        while (groupNames.size() < captureCount - 1) {
            groupNames.add(null);
        }
        groupNames.add(name);
        out.append("(?<g").append(captureCount).append(">(?<m").append(captureCount).append(">)");
    }

    private String parseGroupName() {
        int start = pos;
        while (more() && peek() != '>') {
            char c = peek();
            boolean valid = Character.isLetter(c) || c == '$' || c == '_' || (pos > start && Character.isDigit(c));
            if (!valid) {
                throw error("Invalid capture group name");
            }
            pos++;
        }
        if (!more() || pos == start) {
            throw error("Invalid capture group name");
        }
        pos++;
        return src.substring(start, pos - 1);
    }

    private void backreference(int group) {
        out.append("(?:(?=\\k<m").append(group).append(">)\\k<g").append(group).append(">|(?!\\k<m").append(group)
                .append(">))");
    }

    private void parseAtomEscape() {
        pos++;
        if (!more()) {
            throw error("\\ at end of pattern");
        }
        char c = peek();
        switch (c) {
            case 'd', 'D', 'w', 'W', 's', 'S' -> {
                pos++;
                emitSet(classEscape(c));
            }
            case 'k' -> {
                pos++;
                if (!hasNamedGroups) {
                    literal('k');
                    return;
                }
                if (!more() || peek() != '<') {
                    throw error("Invalid named reference");
                }
                pos++;
                String name = parseGroupName();
                referencedNames.add(name);
                int index = namedGroupIndex(name);
                if (index > 0) {
                    backreference(index);
                } else {
                    out.append("(?:)");
                }
            }
            default -> {
                if (c >= '1' && c <= '9') {
                    int start = pos;
                    while (more() && peek() >= '0' && peek() <= '9') {
                        pos++;
                    }
                    String digits = src.substring(start, pos);
                    if (digits.length() < 6 && Integer.parseInt(digits) <= totalCaptures) {
                        backreference(Integer.parseInt(digits));
                        return;
                    }
                    pos = start;
                }
                literal(characterEscape(false));
            }
        }
    }

    // A named group's number, from the pre-scan of the whole pattern (a reference may come before its group).
    private int namedGroupIndex(String name) {
        int count = 0;
        boolean inClass = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\\') {
                i++;
            } else if (inClass) {
                inClass = c != ']';
            } else if (c == '[') {
                inClass = true;
            } else if (c == '(') {
                boolean named = src.startsWith("(?<", i) && i + 3 < src.length() && src.charAt(i + 3) != '='
                        && src.charAt(i + 3) != '!';
                if (named || !src.startsWith("(?", i)) {
                    count++;
                }
                if (named && src.startsWith(name + ">", i + 3)) {
                    return count;
                }
            }
        }
        return 0;
    }

    // Reads a CharacterEscape after the backslash; returns the code unit it stands for.
    private int characterEscape(boolean inClass) {
        char c = src.charAt(pos++);
        switch (c) {
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'v':
                return 0x0B;
            case 'f':
                return '\f';
            case 'r':
                return '\r';
            case 'c':
                if (more() && isAsciiLetter(peek())) {
                    return src.charAt(pos++) % 32;
                }
                if (inClass && more() && (Character.isDigit(peek()) && peek() < 128 || peek() == '_')) {
                    return src.charAt(pos++) % 32;
                }
                pos--;
                return '\\';
            case 'x':
                if (hexAt(pos, 2)) {
                    pos += 2;
                    return Integer.parseInt(src.substring(pos - 2, pos), 16);
                }
                return 'x';
            case 'u':
                if (hexAt(pos, 4)) {
                    pos += 4;
                    return Integer.parseInt(src.substring(pos - 4, pos), 16);
                }
                return 'u';
            default:
                if (c >= '0' && c <= '7') {
                    return octal(c);
                }
                return c;
        }
    }

    private int octal(char first) {
        int value = first - '0';
        if (more() && peek() >= '0' && peek() <= '7') {
            value = value * 8 + (src.charAt(pos++) - '0');
            if (first <= '3' && more() && peek() >= '0' && peek() <= '7') {
                value = value * 8 + (src.charAt(pos++) - '0');
            }
        }
        return value;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean hexAt(int from, int count) {
        if (from + count > src.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            char c = src.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static int[][] classEscape(char c) {
        return switch (c) {
            case 'd' -> DIGIT;
            case 'D' -> complement(DIGIT);
            case 'w' -> WORD;
            case 'W' -> complement(WORD);
            case 's' -> SPACE;
            default -> complement(SPACE);
        };
    }

    private void parseClass() {
        pos++;
        boolean negated = more() && peek() == '^';
        if (negated) {
            pos++;
        }
        List<int[]> ranges = new ArrayList<>();
        while (true) {
            if (!more()) {
                throw error("Unterminated character class");
            }
            if (peek() == ']') {
                pos++;
                break;
            }
            int[][] first = classAtom();
            if (more() && peek() == '-' && pos + 1 < src.length() && src.charAt(pos + 1) != ']') {
                pos++;
                int[][] second = classAtom();
                if (first.length != 1 || second.length != 1 || first[0][0] != first[0][1]
                        || second[0][0] != second[0][1]) {
                    addAll(ranges, first);
                    ranges.add(new int[] {'-', '-'});
                    addAll(ranges, second);
                } else if (first[0][0] > second[0][0]) {
                    throw error("Range out of order in character class");
                } else {
                    ranges.add(new int[] {first[0][0], second[0][0]});
                }
            } else {
                addAll(ranges, first);
            }
        }
        int[][] set = normalize(ranges);
        if (set.length == 0) {
            out.append(negated ? "(?s:.)" : "(?!)");
            return;
        }
        out.append(negated ? "[^" : "[");
        appendRanges(set);
        out.append(']');
    }

    // A class atom: a single code unit as a one-element range, or the ranges of a class escape.
    private int[][] classAtom() {
        char c = src.charAt(pos);
        if (c != '\\') {
            pos++;
            return new int[][] {{c, c}};
        }
        pos++;
        if (!more()) {
            throw error("\\ at end of pattern");
        }
        char e = peek();
        switch (e) {
            case 'd', 'D', 'w', 'W', 's', 'S':
                pos++;
                return classEscape(e);
            case 'b':
                pos++;
                return new int[][] {{8, 8}};
            case '-':
                pos++;
                return new int[][] {{'-', '-'}};
            default:
                int unit = characterEscape(true);
                return new int[][] {{unit, unit}};
        }
    }

    private static void addAll(List<int[]> ranges, int[][] set) {
        for (int[] range : set) {
            ranges.add(range);
        }
    }

    private static int[][] normalize(List<int[]> ranges) {
        List<int[]> sorted = new ArrayList<>(ranges);
        sorted.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : sorted) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1] + 1) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], range[1]);
            } else {
                merged.add(new int[] {range[0], range[1]});
            }
        }
        return merged.toArray(new int[0][]);
    }

    private static int[][] complement(int[][] set) {
        List<int[]> result = new ArrayList<>();
        int next = 0;
        for (int[] range : set) {
            if (range[0] > next) {
                result.add(new int[] {next, range[0] - 1});
            }
            next = range[1] + 1;
        }
        if (next <= MAX_CHAR) {
            result.add(new int[] {next, Character.MAX_CODE_POINT});
        }
        return result.toArray(new int[0][]);
    }

    private void emitSet(int[][] set) {
        out.append('[');
        appendRanges(set);
        out.append(']');
    }

    private void appendRanges(int[][] set) {
        for (int[] range : set) {
            out.append("\\x{").append(Integer.toHexString(range[0])).append('}');
            if (range[1] != range[0]) {
                out.append("-\\x{").append(Integer.toHexString(range[1])).append('}');
            }
        }
    }

    private void literal(int unit) {
        out.append("\\x{").append(Integer.toHexString(unit)).append('}');
    }
}
