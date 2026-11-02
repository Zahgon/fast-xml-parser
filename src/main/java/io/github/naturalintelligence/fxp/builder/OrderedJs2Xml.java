// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/fast-xml-builder)
// Ported to Java from fast-xml-builder 1.2.0 (src/orderedJs2Xml.js).

package io.github.naturalintelligence.fxp.builder;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.pathexpr.Matcher;
import java.util.List;

/** Builds XML from the ordered form ({@code preserveOrder}): an array of {@code {tag: children, ":@": attrs}}. */
final class OrderedJs2Xml {
    private final JsObject options;
    private final List<Expression> stopNodeExpressions;
    private final Object xmlVersion;
    private final Matcher matcher = new Matcher();

    private OrderedJs2Xml(JsObject options, Object jArray) {
        this.options = options;
        this.stopNodeExpressions = BuilderUtil.stopNodeExpressions(options.get("stopNodes"));
        this.xmlVersion = detectXmlVersionFromArray(jArray);
    }

    static String toXml(Object jArray, JsObject options) {
        String indentation = Js.truthy(options.get("format")) ? "\n" : "";
        OrderedJs2Xml builder = new OrderedJs2Xml(options, jArray);
        return builder.arrToStr(jArray, indentation);
    }

    private Object opt(String name) {
        return options.get(name);
    }

    private Object detectXmlVersionFromArray(Object jArray) {
        if (!(jArray instanceof JsArray array) || array.size() == 0) {
            return "1.0";
        }
        Object first = array.get(0);
        if ("?xml".equals(propName(first))) {
            Object attrs = Js.get(first, ":@");
            if (Js.truthy(attrs)) {
                Object version = Js.get(attrs, Js.toStr(Js.add(opt("attributeNamePrefix"), "version")));
                if (Js.truthy(version)) {
                    return version;
                }
            }
        }
        return "1.0";
    }

    private static String propName(Object obj) {
        for (String key : Js.keys(obj)) {
            if (!key.equals(":@")) {
                return key;
            }
        }
        return null;
    }

    private String arrToStr(Object arr, String indentation) {
        Object maxNestedTags = opt("maxNestedTags");
        if (Js.truthy(maxNestedTags) && Js.greaterThan(matcher.getDepth(), maxNestedTags)) {
            throw JsError.error("Maximum nested tags exceeded");
        }
        if (!(arr instanceof JsArray array)) {
            if (!Js.isNullish(arr)) {
                return Js.toStr(BuilderUtil.replaceEntitiesValue(Js.toStr(arr), options));
            }
            return "";
        }
        StringBuilder xmlStr = new StringBuilder();
        boolean isPreviousElementTag = false;
        for (int i = 0; i < array.size(); i++) {
            Object tagObj = array.get(i);
            String rawTagName = propName(tagObj);
            if (rawTagName == null) {
                continue;
            }
            boolean isSpecialName = Js.strictEquals(rawTagName, opt("textNodeName"))
                    || Js.strictEquals(rawTagName, opt("cdataPropName"))
                    || Js.strictEquals(rawTagName, opt("commentPropName")) || rawTagName.startsWith("?");
            Object tagName = isSpecialName ? rawTagName
                    : BuilderUtil.resolveTagName(rawTagName, false, options, matcher, xmlVersion);
            Object attrMap = Js.get(tagObj, ":@");
            matcher.push(tagName, extractAttributeValues(attrMap), null);
            boolean isStopNode = BuilderUtil.checkStopNode(matcher, stopNodeExpressions);
            if (Js.strictEquals(tagName, opt("textNodeName"))) {
                Object tagText = Js.get(tagObj, rawTagName);
                if (!isStopNode) {
                    tagText = Js.call(opt("tagValueProcessor"), "options.tagValueProcessor", tagName, tagText);
                    tagText = BuilderUtil.replaceEntitiesValue(tagText, options);
                }
                if (isPreviousElementTag) {
                    xmlStr.append(indentation);
                }
                xmlStr.append(Js.toStr(tagText));
                isPreviousElementTag = false;
                matcher.pop();
                continue;
            } else if (Js.strictEquals(tagName, opt("cdataPropName"))) {
                if (isPreviousElementTag) {
                    xmlStr.append(indentation);
                }
                Object val = Js.get(Js.get(Js.get(tagObj, rawTagName), 0), Js.toStr(opt("textNodeName")));
                xmlStr.append("<![CDATA[").append(BuilderUtil.safeCdata(val)).append("]]>");
                isPreviousElementTag = false;
                matcher.pop();
                continue;
            } else if (Js.strictEquals(tagName, opt("commentPropName"))) {
                Object val = Js.get(Js.get(Js.get(tagObj, rawTagName), 0), Js.toStr(opt("textNodeName")));
                xmlStr.append(indentation).append("<!--").append(BuilderUtil.safeComment(val)).append("-->");
                isPreviousElementTag = true;
                matcher.pop();
                continue;
            }
            String name = Js.toStr(tagName);
            if (name.startsWith("?")) {
                String attStr = attrToStr(attrMap, isStopNode);
                String tempInd = name.equals("?xml") ? "" : indentation;
                xmlStr.append(tempInd).append('<').append(name).append(attStr).append("?>");
                isPreviousElementTag = true;
                matcher.pop();
                continue;
            }
            String newIndentation = indentation;
            if (!newIndentation.isEmpty()) {
                newIndentation = Js.toStr(Js.add(newIndentation, opt("indentBy")));
            }
            String attStr = attrToStr(attrMap, isStopNode);
            String tagStart = indentation + "<" + name + attStr;
            String tagValue = isStopNode ? getRawContent(Js.get(tagObj, rawTagName))
                    : arrToStr(Js.get(tagObj, rawTagName), newIndentation);
            if (isUnpaired(tagName)) {
                xmlStr.append(tagStart).append(Js.truthy(opt("suppressUnpairedNode")) ? ">" : "/>");
            } else if (tagValue.isEmpty() && Js.truthy(opt("suppressEmptyNode"))) {
                xmlStr.append(tagStart).append("/>");
            } else if (tagValue.endsWith(">")) {
                xmlStr.append(tagStart).append('>').append(tagValue).append(indentation).append("</").append(name)
                        .append('>');
            } else {
                xmlStr.append(tagStart).append('>');
                if (!tagValue.isEmpty() && !indentation.isEmpty()
                        && (tagValue.contains("/>") || tagValue.contains("</"))) {
                    xmlStr.append(indentation).append(Js.toStr(opt("indentBy"))).append(tagValue).append(indentation);
                } else {
                    xmlStr.append(tagValue);
                }
                xmlStr.append("</").append(name).append('>');
            }
            isPreviousElementTag = true;
            matcher.pop();
        }
        return xmlStr.toString();
    }

    private boolean isUnpaired(Object tagName) {
        Object unpairedTags = opt("unpairedTags");
        if (unpairedTags instanceof JsArray array) {
            return array.items().stream().anyMatch(tag -> Js.strictEquals(tag, tagName));
        }
        if (unpairedTags instanceof String s) {
            return s.contains(Js.toStr(tagName));
        }
        Js.call(Js.get(unpairedTags, "indexOf"), "options.unpairedTags.indexOf", tagName);
        return false;
    }

    private String stripPrefix(String attr) {
        return Js.substringFrom(attr, Js.get(opt("attributeNamePrefix"), "length"));
    }

    private Object extractAttributeValues(Object attrMap) {
        if (!Js.truthy(attrMap) || Js.truthy(opt("ignoreAttributes"))) {
            return null;
        }
        JsObject attrValues = new JsObject();
        boolean hasAttrs = false;
        for (String attr : Js.forInKeys(attrMap)) {
            String cleanAttrName = Js.startsWith(attr, opt("attributeNamePrefix")) ? stripPrefix(attr) : attr;
            attrValues.set(cleanAttrName, BuilderUtil.escapeAttribute(Js.get(attrMap, attr)));
            hasAttrs = true;
        }
        return hasAttrs ? attrValues : null;
    }

    private String getRawContent(Object arr) {
        if (!(arr instanceof JsArray array)) {
            return Js.isNullish(arr) ? "" : Js.toStr(arr);
        }
        StringBuilder content = new StringBuilder();
        for (Object item : array) {
            String tagName = propName(item);
            if (tagName == null) {
                continue;
            }
            if (Js.strictEquals(tagName, opt("textNodeName"))) {
                content.append(Js.toStr(Js.get(item, tagName)));
            } else if (Js.strictEquals(tagName, opt("cdataPropName")) || Js.strictEquals(tagName, opt("commentPropName"))) {
                content.append(Js.toStr(Js.get(Js.get(Js.get(item, tagName), 0), Js.toStr(opt("textNodeName")))));
            } else if (tagName.startsWith("?")) {
                continue;
            } else if (!tagName.isEmpty()) {
                String attStr = attrToStrRaw(Js.get(item, ":@"));
                String nestedContent = getRawContent(Js.get(item, tagName));
                if (nestedContent.isEmpty()) {
                    content.append('<').append(tagName).append(attStr).append("/>");
                } else {
                    content.append('<').append(tagName).append(attStr).append('>').append(nestedContent).append("</")
                            .append(tagName).append('>');
                }
            }
        }
        return content.toString();
    }

    private String attrToStrRaw(Object attrMap) {
        StringBuilder attrStr = new StringBuilder();
        if (Js.truthy(attrMap) && !Js.truthy(opt("ignoreAttributes"))) {
            for (String attr : Js.forInKeys(attrMap)) {
                Object attrVal = Js.get(attrMap, attr);
                if (Js.strictEquals(attrVal, true) && Js.truthy(opt("suppressBooleanAttributes"))) {
                    attrStr.append(' ').append(stripPrefix(attr));
                } else {
                    attrStr.append(' ').append(stripPrefix(attr)).append("=\"").append(BuilderUtil.escapeAttribute(attrVal))
                            .append('"');
                }
            }
        }
        return attrStr.toString();
    }

    private String attrToStr(Object attrMap, boolean isStopNode) {
        StringBuilder attrStr = new StringBuilder();
        if (Js.truthy(attrMap) && !Js.truthy(opt("ignoreAttributes"))) {
            for (String attr : Js.forInKeys(attrMap)) {
                String cleanAttrName = stripPrefix(attr);
                Object resolvedAttrName = isStopNode ? cleanAttrName
                        : BuilderUtil.resolveTagName(cleanAttrName, true, options, matcher, xmlVersion);
                Object attrVal = Js.get(attrMap, attr);
                if (!isStopNode) {
                    attrVal = Js.call(opt("attributeValueProcessor"), "options.attributeValueProcessor", attr, attrVal);
                    attrVal = BuilderUtil.replaceEntitiesValue(attrVal, options);
                }
                if (Js.strictEquals(attrVal, true) && Js.truthy(opt("suppressBooleanAttributes"))) {
                    attrStr.append(' ').append(Js.toStr(resolvedAttrName));
                } else {
                    attrStr.append(' ').append(Js.toStr(resolvedAttrName)).append("=\"")
                            .append(BuilderUtil.escapeAttribute(attrVal)).append('"');
                }
            }
        }
        return attrStr.toString();
    }
}
