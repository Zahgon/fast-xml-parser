// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/xmlparser/node2json.js).

package io.github.naturalintelligence.fxp.parser;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.pathexpr.Matcher;
import io.github.naturalintelligence.fxp.pathexpr.MatcherView;
import java.util.List;

/** Compresses the ordered tree into plain objects: repeated tags become arrays, text-only tags become values. */
final class Node2Json {
    private final JsObject options;
    private final Matcher matcher;
    private final MatcherView readonlyMatcher;

    private Node2Json(JsObject options, Matcher matcher, MatcherView readonlyMatcher) {
        this.options = options;
        this.matcher = matcher;
        this.readonlyMatcher = readonlyMatcher;
    }

    /** {@code prettify(node, options, matcher, readonlyMatcher)}. */
    static JsObject prettify(Object node, JsObject options, Matcher matcher, MatcherView readonlyMatcher) {
        return new Node2Json(options, matcher, readonlyMatcher).compress(node);
    }

    private Object textNodeName() {
        return options.get("textNodeName");
    }

    /**
     * {@code key.startsWith(prefix) ? key.substring(prefix.length) : key}, with JavaScript's conversions and errors.
     *
     * @param key an attribute key
     * @param prefix the attributeNamePrefix option
     * @return the key without the prefix
     */
    static String stripPrefix(String key, Object prefix) {
        return Js.startsWith(key, prefix) ? Js.substringFrom(key, Js.get(prefix, "length")) : key;
    }

    private static Object stripAttributePrefix(Object attrs, Object prefix) {
        if (!Js.truthy(attrs) || !Js.isObjectValue(attrs)) {
            return new JsObject();
        }
        if (!Js.truthy(prefix)) {
            return attrs;
        }
        JsObject rawAttrs = new JsObject();
        for (String key : Js.forInKeys(attrs)) {
            rawAttrs.set(stripPrefix(key, prefix), Js.get(attrs, key));
        }
        return rawAttrs;
    }

    private JsObject compress(Object arr) {
        Object text = Js.UNDEFINED;
        JsObject compressedObj = new JsObject();
        double length = Js.toNumber(Js.get(arr, "length"));
        for (int i = 0; i < length; i++) {
            Object tagObj = Js.get(arr, i);
            String property = propName(tagObj);
            boolean isTag = property != null && !Js.strictEquals(property, textNodeName());
            if (isTag) {
                Object attrs = Js.get(tagObj, ":@");
                matcher.push(property, stripAttributePrefix(Js.truthy(attrs) ? attrs : new JsObject(),
                        options.get("attributeNamePrefix")), null);
            }
            if (property != null && !isTag) {
                Object value = Js.get(tagObj, property);
                text = text == Js.UNDEFINED ? value : Js.add(text, Js.add("", value));
            } else if (property == null) {
                continue;
            } else if (Js.truthy(Js.get(tagObj, property))) {
                compressTag(compressedObj, tagObj, property);
                matcher.pop();
            }
        }
        if (text instanceof String s) {
            if (!s.isEmpty()) {
                compressedObj.set(Js.toStr(textNodeName()), s);
            }
        } else if (text != Js.UNDEFINED) {
            compressedObj.set(Js.toStr(textNodeName()), text);
        }
        return compressedObj;
    }

    private void compressTag(JsObject compressedObj, Object tagObj, String property) {
        String textKey = Js.toStr(textNodeName());
        boolean alwaysCreateTextNode = Js.truthy(options.get("alwaysCreateTextNode"));
        Object val = compress(Js.get(tagObj, property));
        JsObject compressed = (JsObject) val;
        boolean isLeaf = isLeafTag(compressed);
        if (compressed.size() == 0 && alwaysCreateTextNode) {
            compressed.set(textKey, "");
        }
        Object attributes = Js.get(tagObj, ":@");
        if (Js.truthy(attributes)) {
            assignAttributes(compressed, attributes);
        } else if (compressed.size() == 1 && compressed.get(textKey) != Js.UNDEFINED && !alwaysCreateTextNode) {
            val = compressed.get(textKey);
        } else if (compressed.size() == 0) {
            if (alwaysCreateTextNode) {
                compressed.set(textKey, "");
            } else {
                val = "";
            }
        }
        if (tagObj instanceof JsObject entry && entry.metadata() != Js.UNDEFINED && val instanceof JsObject object) {
            object.setMetadata(entry.metadata());
        }
        Object existing = compressedObj.get(property);
        if (existing != Js.UNDEFINED && compressedObj.hasOwn(property)) {
            if (!(existing instanceof JsArray)) {
                existing = JsArray.of(existing);
                compressedObj.set(property, existing);
            }
            ((JsArray) existing).push(val);
        } else {
            Object jPathOrMatcher = Js.truthy(options.get("jPath")) ? readonlyMatcher.toString() : readonlyMatcher;
            Object isArray = Js.call(options.get("isArray"), "options.isArray", property, jPathOrMatcher, isLeaf);
            compressedObj.set(property, Js.truthy(isArray) ? JsArray.of(val) : val);
        }
    }

    private static String propName(Object obj) {
        List<String> keys = Js.keys(obj);
        for (String key : keys) {
            if (!key.equals(":@")) {
                return key;
            }
        }
        return null;
    }

    private void assignAttributes(JsObject obj, Object attrMap) {
        Object prefix = options.get("attributeNamePrefix");
        for (String attrName : Js.keys(attrMap)) {
            String rawAttrName = stripPrefix(attrName, prefix);
            Object jPathOrMatcher = Js.truthy(options.get("jPath")) ? readonlyMatcher.toString() + "." + rawAttrName
                    : readonlyMatcher;
            Object isArray = Js.call(options.get("isArray"), "options.isArray", attrName, jPathOrMatcher, true, true);
            Object value = Js.get(attrMap, attrName);
            obj.set(attrName, Js.truthy(isArray) ? JsArray.of(value) : value);
        }
    }

    private boolean isLeafTag(JsObject obj) {
        int propCount = obj.size();
        if (propCount == 0) {
            return true;
        }
        Object text = obj.get(Js.toStr(textNodeName()));
        return propCount == 1 && (Js.truthy(text) || text instanceof Boolean || Js.strictEquals(text, 0.0));
    }
}
