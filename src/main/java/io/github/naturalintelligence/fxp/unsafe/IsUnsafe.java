// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/is-unsafe)
// Ported to Java from is-unsafe 2.0.0 (src/index.js, src/contexts/html.js, src/contexts/xml.js).

package io.github.naturalintelligence.fxp.unsafe;

import io.github.naturalintelligence.fxp.js.JsRegExp;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code isUnsafe(value, [HTML, XML])} of is-unsafe: whether a string matches any rule of its HTML or XML context. The
 * rules are the package's own JavaScript regular expressions, run with JavaScript semantics.
 */
public final class IsUnsafe {
    private static final List<Rule> HTML = List.of(
            regex("<script[\\s>/]", "i"),
            regex("<\\/script[\\s>]", "i"),
            regex("j[\\t\\n\\r ]*a[\\t\\n\\r ]*v[\\t\\n\\r ]*a[\\t\\n\\r ]*s[\\t\\n\\r ]*c[\\t\\n\\r ]*r[\\t\\n\\r ]*"
                    + "i[\\t\\n\\r ]*p[\\t\\n\\r ]*t[\\t\\n\\r ]*:", "i"),
            regex("vbscript[\\t\\n\\r ]*:", "i"),
            regex("data[\\t\\n\\r ]*:[\\t\\n\\r ]*text\\/html", "i"),
            regex("data[\\t\\n\\r ]*:[\\t\\n\\r ]*application\\/xhtml", "i"),
            regex("data[\\t\\n\\r ]*:[\\t\\n\\r ]*image\\/svg\\+xml", "i"),
            regex("\\bon\\w{1,30}\\s*=", "i"),
            regex("(?:&#x0*3[Cc];?|&#0*60;?|&lt;)\\s*script", "i"),
            regex("(?:&#x0*6[Aa];?|&#0*106;?)\\s*(?:&#x0*61;?|a)[\\s\\S]{0,80}script\\s*:", "i"),
            regex("style[\\s\\S]{0,20}expression\\s*\\(", "i"),
            regex("<(?:object|embed)[\\s>/]", "i"),
            regex("<base[\\s>]", "i"),
            regex("<meta[\\s\\S]{0,40}http-equiv[\\s\\S]{0,20}refresh", "i"),
            regex("srcdoc\\s*=", "i"),
            regex("<iframe[\\s>/]", "i"),
            regex("<form[\\s>/]", "i"));

    private static final List<Rule> XML = List.of(
            regex("<!\\[CDATA\\[", "i"),
            regex("\\]\\]>", ""),
            regex("<\\?(?:xml[\\- ]|php|asp)", "i"),
            regex("<!DOCTYPE(?:[\\s[]|$)", "i"),
            regex("\\bSYSTEM\\s+[\"']", "i"),
            regex("\\bPUBLIC\\s+[\"']", "i"),
            regex("<!ENTITY[\\s%]", "i"),
            regex("(?:&\\w{1,20};){3,}", ""),
            regex("\\bxmlns\\s*(?::\\w{1,40})?\\s*=", "i"),
            regex("<!--", ""),
            regex("-->", ""),
            regex("\\?>", ""));

    private final List<JsRegExp> rules = Stream.concat(HTML.stream(), XML.stream())
            .map(rule -> JsRegExp.create(rule.source(), rule.flags())).toList();

    /** One rule: the text of a JavaScript regular expression. */
    private record Rule(String source, String flags) {
    }

    private static Rule regex(String source, String flags) {
        return new Rule(source, flags);
    }

    /**
     * {@code isUnsafe(value, [HTML, XML])}.
     *
     * @param value the string
     * @return whether any HTML or XML rule matches it
     */
    public boolean isUnsafeInHtmlOrXml(String value) {
        return rules.stream().anyMatch(rule -> rule.test(value));
    }
}
