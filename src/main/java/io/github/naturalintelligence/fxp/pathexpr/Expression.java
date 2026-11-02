// SPDX-License-Identifier: MIT
// Copyright (c) 2024 Amit Gupta (github.com/NaturalIntelligence/path-expression-matcher)
// Ported to Java from path-expression-matcher 1.6.2 (src/Expression.js).

package io.github.naturalintelligence.fxp.pathexpr;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsHost;
import io.github.naturalintelligence.fxp.js.JsNumber;
import io.github.naturalintelligence.fxp.js.JsString;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed tag-path pattern such as {@code root.users.user}, {@code ..user[id=1]:first} or {@code ns::tag:nth(2)}.
 */
public final class Expression implements JsHost {
    private static final Pattern BRACKET = Pattern.compile("([^\\[]+)(\\[[^\\]]*\\])([^\\n\\r\\u2028\\u2029]*)");
    private static final Pattern NTH = Pattern.compile("nth\\(([0-9]+)\\)");

    private final String pattern;
    private final Object separator;
    private final List<Segment> segments;

    /** One segment of a pattern: a tag test or a deep wildcard ({@code ..}). */
    public record Segment(boolean deepWildcard, String tag, String namespace, String attrName, String attrValue,
            String position, double positionValue) {
    }

    /**
     * {@code new Expression(pattern, {separator})}.
     *
     * @param pattern the pattern
     * @param separator the separator option ({@code undefined} or any falsy value: ".")
     * @throws JsError for an empty namespace or an empty tag
     */
    public Expression(String pattern, Object separator) {
        this.pattern = pattern;
        this.separator = Js.truthy(separator) ? separator : ".";
        this.segments = parse(pattern);
    }

    /**
     * {@code new Expression(pattern)}.
     *
     * @param pattern the pattern
     */
    public Expression(String pattern) {
        this(pattern, Js.UNDEFINED);
    }

    private boolean isSeparator(String text, int index) {
        return index < text.length() && Js.strictEquals(String.valueOf(text.charAt(index)), separator);
    }

    private List<Segment> parse(String text) {
        List<Segment> parsed = new ArrayList<>();
        int i = 0;
        StringBuilder currentPart = new StringBuilder();
        while (i < text.length()) {
            if (isSeparator(text, i)) {
                if (isSeparator(text, i + 1)) {
                    flush(parsed, currentPart);
                    parsed.add(new Segment(true, null, null, null, null, null, 0));
                    i += 2;
                } else {
                    flush(parsed, currentPart);
                    i++;
                }
            } else {
                currentPart.append(text.charAt(i));
                i++;
            }
        }
        flush(parsed, currentPart);
        return parsed;
    }

    private void flush(List<Segment> parsed, StringBuilder currentPart) {
        String part = JsString.trim(currentPart.toString());
        if (!part.isEmpty()) {
            parsed.add(parseSegment(part));
        }
        currentPart.setLength(0);
    }

    private static Segment parseSegment(String part) {
        String bracketContent = null;
        String withoutBrackets = part;
        Matcher bracket = BRACKET.matcher(part);
        if (bracket.matches()) {
            withoutBrackets = bracket.group(1) + bracket.group(3);
            String content = bracket.group(2).substring(1, bracket.group(2).length() - 1);
            if (!content.isEmpty()) {
                bracketContent = content;
            }
        }
        String namespace = null;
        String tagAndPosition = withoutBrackets;
        if (withoutBrackets.contains("::")) {
            int nsIndex = withoutBrackets.indexOf("::");
            namespace = JsString.trim(withoutBrackets.substring(0, nsIndex));
            tagAndPosition = JsString.trim(withoutBrackets.substring(nsIndex + 2));
            if (namespace.isEmpty()) {
                throw JsError.error("Invalid namespace in pattern: " + part);
            }
        }
        String tag;
        String positionMatch = null;
        if (tagAndPosition.contains(":")) {
            int colonIndex = tagAndPosition.lastIndexOf(':');
            String tagPart = JsString.trim(tagAndPosition.substring(0, colonIndex));
            String posPart = JsString.trim(tagAndPosition.substring(colonIndex + 1));
            boolean isPositionKeyword = posPart.equals("first") || posPart.equals("last") || posPart.equals("odd")
                    || posPart.equals("even") || NTH.matcher(posPart).matches();
            if (isPositionKeyword) {
                tag = tagPart;
                positionMatch = posPart;
            } else {
                tag = tagAndPosition;
            }
        } else {
            tag = tagAndPosition;
        }
        if (tag.isEmpty()) {
            throw JsError.error("Invalid segment pattern: " + part);
        }
        String attrName = null;
        String attrValue = null;
        if (bracketContent != null) {
            int eqIndex = bracketContent.indexOf('=');
            if (eqIndex >= 0) {
                attrName = JsString.trim(bracketContent.substring(0, eqIndex));
                attrValue = JsString.trim(bracketContent.substring(eqIndex + 1));
            } else {
                attrName = JsString.trim(bracketContent);
            }
        }
        String position = null;
        double positionValue = 0;
        if (positionMatch != null) {
            Matcher nth = NTH.matcher(positionMatch);
            if (nth.matches()) {
                position = "nth";
                positionValue = JsNumber.parseInt(nth.group(1), 10);
            } else {
                position = positionMatch;
            }
        }
        return new Segment(false, tag, namespace, attrName, attrValue, position, positionValue);
    }

    /**
     * The pattern text.
     *
     * @return the pattern
     */
    public String pattern() {
        return pattern;
    }

    /**
     * The parsed segments.
     *
     * @return the segments
     */
    public List<Segment> segments() {
        return segments;
    }

    /**
     * Whether the pattern has a {@code ..} segment.
     *
     * @return true when it has one
     */
    public boolean hasDeepWildcard() {
        return segments.stream().anyMatch(Segment::deepWildcard);
    }

    @Override
    public String jsToString() {
        return pattern;
    }

    @Override
    public String toString() {
        return pattern;
    }
}
