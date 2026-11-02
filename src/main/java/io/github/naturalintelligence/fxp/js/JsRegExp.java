// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A JavaScript {@code RegExp}: {@code new RegExp(source, flags)} with V8's syntax errors, {@code lastIndex} and the
 * {@code test}, {@code exec} and {@code String.prototype.replace} behaviour of the {@code g} and {@code y} flags.
 */
public final class JsRegExp {
    private final String source;
    private final String flags;
    private final Pattern pattern;
    private final int captureCount;
    private final List<String> groupNames;
    private final boolean global;
    private final boolean sticky;
    private double lastIndex;

    private JsRegExp(String source, String flags) {
        validateFlags(flags);
        RegExpTranslator.Result translated = RegExpTranslator.translate(source, flags);
        this.source = source;
        this.flags = flags;
        try {
            this.pattern = Pattern.compile(translated.javaSource(), flags.indexOf('i') >= 0 ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException error) {
            // A JavaScript pattern the translation could not express in Java: a SyntaxError, as a bad pattern is.
            throw JsError.syntaxError("Invalid regular expression: /" + source + "/" + flags + ": " + error.getDescription());
        }
        this.captureCount = translated.captureCount();
        this.groupNames = translated.groupNames();
        this.global = flags.indexOf('g') >= 0;
        this.sticky = flags.indexOf('y') >= 0;
    }

    /**
     * {@code new RegExp(source, flags)}.
     *
     * @param source the pattern
     * @param flags the flags
     * @return the regular expression
     * @throws JsError a SyntaxError with V8's message for a bad pattern or bad flags
     */
    public static JsRegExp create(String source, String flags) {
        return new JsRegExp(source, flags);
    }

    private static void validateFlags(String flags) {
        boolean valid = true;
        for (int i = 0; i < flags.length(); i++) {
            char c = flags.charAt(i);
            if ("dgimsuvy".indexOf(c) < 0 || flags.indexOf(c, i + 1) >= 0) {
                valid = false;
            }
        }
        if (!valid || (flags.indexOf('u') >= 0 && flags.indexOf('v') >= 0)) {
            throw JsError.syntaxError("Invalid flags supplied to RegExp constructor '" + flags + "'");
        }
    }

    /** One successful match: where it starts and ends, the captures (null for one that took no part). */
    public record Match(int index, int end, String[] captures) {
    }

    /**
     * {@code RegExpBuiltinExec}: matches from {@code lastIndex} when global or sticky (sticky: only there), from 0
     * otherwise, and updates {@code lastIndex} as JavaScript does.
     *
     * @param input the string
     * @return the match, or null
     */
    public Match exec(String input) {
        long start = global || sticky ? toLength(lastIndex) : 0;
        if (start > input.length()) {
            if (global || sticky) {
                lastIndex = 0;
            }
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        boolean found;
        if (sticky) {
            matcher.region((int) start, input.length());
            matcher.useTransparentBounds(true);
            matcher.useAnchoringBounds(false);
            found = matcher.lookingAt();
        } else {
            found = matcher.find((int) start);
        }
        if (!found) {
            if (global || sticky) {
                lastIndex = 0;
            }
            return null;
        }
        if (global || sticky) {
            lastIndex = matcher.end();
        }
        String[] captures = new String[captureCount + 1];
        captures[0] = matcher.group();
        for (int k = 1; k <= captureCount; k++) {
            captures[k] = matcher.group("g" + k);
        }
        return new Match(matcher.start(), matcher.end(), captures);
    }

    /**
     * Every match from the start of the string, each search starting where the previous match ended (one further
     * after an empty match), as a {@code g} expression's exec loop finds them; {@code lastIndex} is not touched.
     *
     * @param input the string
     * @return the matches in order
     */
    public List<Match> matchAll(String input) {
        List<Match> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        int from = 0;
        while (from <= input.length() && matcher.find(from)) {
            String[] captures = new String[captureCount + 1];
            captures[0] = matcher.group();
            for (int k = 1; k <= captureCount; k++) {
                captures[k] = matcher.group("g" + k);
            }
            matches.add(new Match(matcher.start(), matcher.end(), captures));
            from = matcher.end() == matcher.start() ? matcher.end() + 1 : matcher.end();
        }
        return matches;
    }

    private static long toLength(double value) {
        if (Double.isNaN(value) || value <= 0) {
            return 0;
        }
        return (long) Math.min(value, 9007199254740991.0);
    }

    /**
     * {@code RegExp.prototype.test}.
     *
     * @param input the string
     * @return whether it matches
     */
    public boolean test(String input) {
        return exec(input) != null;
    }

    /**
     * {@code String.prototype.replace(searchValue, replaceValue)} on a string.
     *
     * @param input the string
     * @param searchValue a RegExp, or any value used as a literal search string
     * @param replaceValue a replacement pattern string (any value is converted) or a function
     * @return the replaced string
     */
    public static String replace(String input, Object searchValue, Object replaceValue) {
        Object replacement = replaceValue instanceof JsFunction ? replaceValue : Js.toStr(replaceValue);
        if (searchValue instanceof JsRegExp regexp) {
            return regexp.replaceAll(input, replacement);
        }
        String search = Js.toStr(searchValue);
        int position = input.indexOf(search);
        if (position < 0) {
            return input;
        }
        String replaced = replacement instanceof JsFunction function
                ? Js.toStr(function.call(search, (double) position, input))
                : substitute(search, input, position, new String[] {search}, null, (String) replacement);
        return input.substring(0, position) + replaced + input.substring(position + search.length());
    }

    private String replaceAll(String input, Object replacement) {
        List<Match> results = new ArrayList<>();
        if (global) {
            lastIndex = 0;
        }
        while (true) {
            Match match = exec(input);
            if (match == null) {
                break;
            }
            results.add(match);
            if (!global) {
                break;
            }
            if (match.captures()[0].isEmpty()) {
                lastIndex = toLength(lastIndex) + 1;
            }
        }
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (Match match : results) {
            String replaced;
            if (replacement instanceof JsFunction function) {
                List<Object> args = new ArrayList<>();
                for (String capture : match.captures()) {
                    args.add(capture == null ? Js.UNDEFINED : capture);
                }
                args.add((double) match.index());
                args.add(input);
                if (hasNamedGroups()) {
                    args.add(namedCaptures(match));
                }
                replaced = Js.toStr(function.call(args.toArray()));
            } else {
                replaced = substitute(match.captures()[0], input, match.index(), match.captures(),
                        hasNamedGroups() ? namedCaptures(match) : null, (String) replacement);
            }
            if (match.index() >= next) {
                out.append(input, next, match.index()).append(replaced);
                next = match.index() + match.captures()[0].length();
            }
        }
        return out.append(input.substring(Math.min(next, input.length()))).toString();
    }

    private boolean hasNamedGroups() {
        return groupNames.stream().anyMatch(name -> name != null);
    }

    private JsObject namedCaptures(Match match) {
        JsObject groups = JsObject.withNullPrototype();
        for (int k = 0; k < groupNames.size(); k++) {
            if (groupNames.get(k) != null) {
                String capture = match.captures()[k + 1];
                groups.defineOwn(groupNames.get(k), capture == null ? Js.UNDEFINED : capture);
            }
        }
        return groups;
    }

    // GetSubstitution with V8's reading of $n and $nn.
    private static String substitute(String matched, String input, int position, String[] captures,
            JsObject namedCaptures, String replacement) {
        StringBuilder out = new StringBuilder();
        int m = captures.length - 1;
        for (int i = 0; i < replacement.length(); i++) {
            char c = replacement.charAt(i);
            if (c != '$' || i + 1 >= replacement.length()) {
                out.append(c);
                continue;
            }
            char next = replacement.charAt(i + 1);
            if (next == '$') {
                out.append('$');
                i++;
            } else if (next == '&') {
                out.append(matched);
                i++;
            } else if (next == '`') {
                out.append(input, 0, position);
                i++;
            } else if (next == '\'') {
                out.append(input.substring(Math.min(position + matched.length(), input.length())));
                i++;
            } else if (next >= '0' && next <= '9') {
                int index = next - '0';
                int advance = 1;
                if (i + 2 < replacement.length()) {
                    char second = replacement.charAt(i + 2);
                    if (second >= '0' && second <= '9' && index * 10 + (second - '0') <= m) {
                        index = index * 10 + (second - '0');
                        advance = 2;
                    }
                }
                if (index == 0 || index > m) {
                    out.append('$');
                } else {
                    if (captures[index] != null) {
                        out.append(captures[index]);
                    }
                    i += advance;
                }
            } else if (next == '<' && namedCaptures != null) {
                int close = replacement.indexOf('>', i + 2);
                if (close < 0) {
                    out.append('$');
                } else {
                    Object capture = namedCaptures.get(replacement.substring(i + 2, close));
                    if (capture != Js.UNDEFINED) {
                        out.append(Js.toStr(capture));
                    }
                    i = close;
                }
            } else {
                out.append('$');
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "/" + (source.isEmpty() ? "(?:)" : source.replace("/", "\\/")) + "/"
                + RegExpTranslator.canonicalFlags(flags);
    }
}
