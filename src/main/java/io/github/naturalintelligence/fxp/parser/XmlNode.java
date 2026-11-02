// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/xmlparser/xmlNode.js).

package io.github.naturalintelligence.fxp.parser;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsObject;

/** A node of the ordered tree: its tag name, its children in order and its attribute map ({@code ":@"}). */
final class XmlNode {
    Object tagname;
    final JsArray child = new JsArray();
    Object attributes = JsObject.withNullPrototype();

    XmlNode(Object tagname) {
        this.tagname = tagname;
    }

    void add(Object key, Object val) {
        String name = Js.toStr(key);
        if (name.equals("__proto__")) {
            name = "#__proto__";
        }
        JsObject entry = new JsObject();
        entry.defineOwn(name, val);
        child.push(entry);
    }

    void addChild(XmlNode node, Object startIndex) {
        if (Js.strictEquals(node.tagname, "__proto__")) {
            node.tagname = "#__proto__";
        }
        JsObject entry = new JsObject();
        entry.defineOwn(Js.toStr(node.tagname), node.child);
        if (Js.truthy(node.attributes) && Js.keys(node.attributes).size() > 0) {
            entry.defineOwn(":@", node.attributes);
        }
        child.push(entry);
        addStartIndex(startIndex);
    }

    private void addStartIndex(Object startIndex) {
        if (startIndex != Js.UNDEFINED) {
            JsObject metadata = new JsObject();
            metadata.set("startIndex", startIndex);
            ((JsObject) child.get(child.size() - 1)).setMetadata(metadata);
        }
    }

    void addEndIndex(double endIndex) {
        Object last = child.get(child.size() - 1);
        if (last instanceof JsObject lastChild && lastChild.metadata() instanceof JsObject metadata
                && metadata.get("endIndex") == Js.UNDEFINED) {
            metadata.set("endIndex", endIndex);
        }
    }
}
