// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/fast-xml-builder)
// Ported to Java from fast-xml-builder 1.2.0 (src/util.js, src/ignoreAttributes.js and the shared helpers of
// src/fxb.js and src/orderedJs2Xml.js).

package io.github.naturalintelligence.fxp.builder;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsRegExp;
import io.github.naturalintelligence.fxp.naming.XmlNaming;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.pathexpr.Matcher;
import java.util.ArrayList;
import java.util.List;

/** Escaping, entity replacement, name resolution and stop-node checks shared by both builders. */
final class BuilderUtil {
    private BuilderUtil() {
    }

    /** {@code safeComment(val)}: no "--" inside, no "-" at the end. */
    static String safeComment(Object val) {
        String text = Js.toStr(val).replace("--", "- -").replace("--", "- -");
        return text.endsWith("-") ? text.substring(0, text.length() - 1) + "- " : text;
    }

    /** {@code safeCdata(val)}: splits every "]]>" across two CDATA sections. */
    static String safeCdata(Object val) {
        return Js.toStr(val).replace("]]>", "]]]]><![CDATA[>");
    }

    /** {@code escapeAttribute(val)}: escapes both quote characters. */
    static String escapeAttribute(Object val) {
        return Js.toStr(val).replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** The default entity list: {@code &}, {@code >}, {@code <}, {@code '} and {@code "} in that order. */
    static JsArray defaultEntities() {
        JsArray entities = new JsArray();
        String[][] pairs = {{"&", "&amp;"}, {">", "&gt;"}, {"<", "&lt;"}, {"'", "&apos;"}, {"\"", "&quot;"}};
        for (String[] pair : pairs) {
            JsObject entity = new JsObject();
            entity.set("regex", JsRegExp.create(pair[0], "g"));
            entity.set("val", pair[1]);
            entities.push(entity);
        }
        return entities;
    }

    /** {@code replaceEntitiesValue(textValue)}: applies every entity of the options to a non-empty string. */
    static Object replaceEntitiesValue(Object textValue, JsObject options) {
        if (Js.truthy(textValue) && Js.greaterThan(Js.get(textValue, "length"), 0.0)
                && Js.truthy(options.get("processEntities"))) {
            Object entities = options.get("entities");
            double count = Js.toNumber(Js.get(entities, "length"));
            Object value = textValue;
            for (int i = 0; i < count; i++) {
                Object entity = Js.get(entities, i);
                if (!(value instanceof String text)) {
                    throw JsError.typeError("textValue.replace is not a function");
                }
                value = JsRegExp.replace(text, Js.get(entity, "regex"), Js.get(entity, "val"));
            }
            return value;
        }
        return textValue;
    }

    /**
     * {@code resolveTagName}: names pass as they are unless sanitizeName is set and the name is not a QName of the
     * document's XML version ("1.1" selects the XML 1.1 production).
     */
    static Object resolveTagName(String name, boolean isAttribute, JsObject options, Matcher matcher,
            Object xmlVersion) {
        Object sanitizeName = options.get("sanitizeName");
        if (!Js.truthy(sanitizeName)) {
            return name;
        }
        boolean valid = Js.strictEquals(xmlVersion, "1.1") ? XmlNaming.qName11(name) : XmlNaming.qName(name);
        if (valid) {
            return name;
        }
        JsObject context = new JsObject();
        context.set("isAttribute", isAttribute);
        context.set("matcher", matcher.readOnly());
        return Js.call(sanitizeName, "options.sanitizeName", name, context);
    }

    /** The stopNodes option compiled into expressions (strings and Expression objects; other entries skipped). */
    static List<Expression> stopNodeExpressions(Object stopNodes) {
        List<Expression> expressions = new ArrayList<>();
        if (Js.truthy(stopNodes) && stopNodes instanceof JsArray array) {
            for (Object node : array) {
                if (node instanceof String pattern) {
                    expressions.add(new Expression(pattern));
                } else if (node instanceof Expression expression) {
                    expressions.add(expression);
                }
            }
        }
        return expressions;
    }

    static boolean checkStopNode(Matcher matcher, List<Expression> expressions) {
        for (Expression expression : expressions) {
            if (matcher.matches(expression)) {
                return true;
            }
        }
        return false;
    }
}
