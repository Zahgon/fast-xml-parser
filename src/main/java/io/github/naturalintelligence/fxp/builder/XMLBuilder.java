// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Natural Intelligence (github.com/NaturalIntelligence/fast-xml-builder)
// Ported to Java from fast-xml-builder 1.2.0 (src/fxb.js), the XMLBuilder fast-xml-parser re-exports.

package io.github.naturalintelligence.fxp.builder;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.parser.OptionsBuilder;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.pathexpr.Matcher;
import java.util.List;

/** Builds XML text from JavaScript-style values, as fast-xml-builder's {@code XMLBuilder} does. */
public final class XMLBuilder {
    private final JsObject options;
    private final List<Expression> stopNodeExpressions;
    private final boolean attributesDisabled;
    private final JsFunction ignoreAttributesFn;
    private final Object attrPrefixLen;
    private final boolean format;
    private final String tagEndChar;
    private final String newLine;

    /**
     * {@code new XMLBuilder(options)}.
     *
     * @param userOptions the options, any value ({@code undefined} for the defaults)
     */
    public XMLBuilder(Object userOptions) {
        options = defaultOptions();
        Js.assign(options, userOptions);
        Object stopNodes = options.get("stopNodes");
        if (Js.truthy(stopNodes) && stopNodes instanceof JsArray array) {
            JsArray mapped = new JsArray();
            for (Object node : array) {
                mapped.push(node instanceof String s && s.startsWith("*.") ? ".." + s.substring(2) : node);
            }
            options.set("stopNodes", mapped);
        }
        stopNodeExpressions = BuilderUtil.stopNodeExpressions(options.get("stopNodes"));
        attributesDisabled = Js.strictEquals(options.get("ignoreAttributes"), true)
                || Js.truthy(options.get("attributesGroupName"));
        if (attributesDisabled) {
            ignoreAttributesFn = null;
            attrPrefixLen = Js.UNDEFINED;
        } else {
            ignoreAttributesFn = OptionsBuilder.ignoreAttributesFn(options.get("ignoreAttributes"));
            attrPrefixLen = Js.get(options.get("attributeNamePrefix"), "length");
        }
        format = Js.truthy(options.get("format"));
        tagEndChar = format ? ">\n" : ">";
        newLine = format ? "\n" : "";
    }

    private static JsObject defaultOptions() {
        JsObject defaults = new JsObject();
        defaults.set("attributeNamePrefix", "@_");
        defaults.set("attributesGroupName", false);
        defaults.set("textNodeName", "#text");
        defaults.set("ignoreAttributes", true);
        defaults.set("cdataPropName", false);
        defaults.set("format", false);
        defaults.set("indentBy", "  ");
        defaults.set("suppressEmptyNode", false);
        defaults.set("suppressUnpairedNode", true);
        defaults.set("suppressBooleanAttributes", true);
        defaults.set("tagValueProcessor", (JsFunction) args -> OptionsBuilder.arg(args, 1));
        defaults.set("attributeValueProcessor", (JsFunction) args -> OptionsBuilder.arg(args, 1));
        defaults.set("preserveOrder", false);
        defaults.set("commentPropName", false);
        defaults.set("unpairedTags", new JsArray());
        defaults.set("entities", BuilderUtil.defaultEntities());
        defaults.set("processEntities", true);
        defaults.set("stopNodes", new JsArray());
        defaults.set("oneListGroup", false);
        defaults.set("maxNestedTags", 100.0);
        defaults.set("jPath", true);
        defaults.set("sanitizeName", false);
        return defaults;
    }

    /**
     * {@code builder.build(jObj)}.
     *
     * @param data the value to build from (an object, or with preserveOrder an array of entries)
     * @return the XML text
     * @throws JsError when the value or the options lead the original into an error
     */
    public Object build(Object data) {
        if (Js.truthy(options.get("preserveOrder"))) {
            return OrderedJs2Xml.toXml(data, options);
        }
        Object jObj = data;
        Object arrayNodeName = options.get("arrayNodeName");
        if (jObj instanceof JsArray && Js.truthy(arrayNodeName)
                && Js.greaterThan(Js.get(arrayNodeName, "length"), 1.0)) {
            JsObject wrapper = new JsObject();
            wrapper.defineOwn(Js.toStr(arrayNodeName), jObj);
            jObj = wrapper;
        }
        Matcher matcher = new Matcher();
        Object xmlVersion = detectXmlVersionFromObj(jObj);
        return j2x(jObj, 0, matcher, xmlVersion).val();
    }

    private Object opt(String name) {
        return options.get(name);
    }

    private Object detectXmlVersionFromObj(Object jObj) {
        Object decl = Js.get(jObj, "?xml");
        if (Js.truthy(decl) && Js.typeOf(decl).equals("object")) {
            Object groupName = opt("attributesGroupName");
            String versionKey = Js.toStr(Js.add(opt("attributeNamePrefix"), "version"));
            if (Js.truthy(groupName) && Js.truthy(Js.get(decl, Js.toStr(groupName)))) {
                Object v = Js.get(Js.get(decl, Js.toStr(groupName)), versionKey);
                if (Js.truthy(v)) {
                    return v;
                }
            }
            Object v = Js.get(decl, versionKey);
            if (Js.truthy(v)) {
                return v;
            }
        }
        return "1.0";
    }

    private String indentate(int level) {
        if (!format) {
            return "";
        }
        Object indentBy = opt("indentBy");
        if (!(indentBy instanceof String text)) {
            throw JsError.typeError("this.options.indentBy.repeat is not a function");
        }
        return text.repeat(level);
    }

    // isAttribute(name): the name without the prefix, or false (also when attributes are disabled).
    private Object isAttribute(String name) {
        if (attributesDisabled) {
            return false;
        }
        if (Js.startsWith(name, opt("attributeNamePrefix")) && !Js.strictEquals(name, opt("textNodeName"))) {
            return Js.substringFrom(name, attrPrefixLen);
        }
        return false;
    }

    private boolean checkStopNode(Matcher matcher) {
        return BuilderUtil.checkStopNode(matcher, stopNodeExpressions);
    }

    private static boolean startsWithQuestion(Object key) {
        return Js.toStr(key).startsWith("?");
    }

    private record Built(String attrStr, String val) {
    }

    private Built j2x(Object jObj, int level, Matcher matcher, Object xmlVersion) {
        StringBuilder attrStr = new StringBuilder();
        StringBuilder val = new StringBuilder();
        Object maxNestedTags = opt("maxNestedTags");
        if (Js.truthy(maxNestedTags) && Js.greaterOrEqual(matcher.getDepth(), maxNestedTags)) {
            throw JsError.error("Maximum nested tags exceeded");
        }
        Object jPath = Js.truthy(opt("jPath")) ? matcher.toString() : matcher;
        boolean isCurrentStopNode = checkStopNode(matcher);
        for (String key : Js.forInKeys(jObj)) {
            Object attrKey = isAttribute(key);
            boolean isSpecialKey = Js.strictEquals(key, opt("textNodeName")) || Js.strictEquals(key, opt("cdataPropName"))
                    || Js.strictEquals(key, opt("commentPropName"))
                    || (Js.truthy(opt("attributesGroupName")) && Js.strictEquals(key, opt("attributesGroupName")))
                    || Js.truthy(attrKey) || key.startsWith("?");
            Object resolvedKey = isSpecialKey ? key
                    : BuilderUtil.resolveTagName(key, false, options, matcher, xmlVersion);
            Object value = Js.get(jObj, key);
            if (value == Js.UNDEFINED) {
                continue;
            } else if (value == null) {
                if (Js.truthy(attrKey) || Js.strictEquals(resolvedKey, opt("cdataPropName"))
                        || Js.strictEquals(resolvedKey, opt("commentPropName"))) {
                    continue;
                }
                val.append(indentate(level)).append('<').append(Js.toStr(resolvedKey))
                        .append(startsWithQuestion(resolvedKey) ? "?" : "/").append(tagEndChar);
            } else if (!Js.typeOf(value).equals("object")) {
                if (Js.truthy(attrKey)) {
                    if (!Js.truthy(ignoreAttributesFn.call(attrKey, jPath))) {
                        Object resolvedAttr = BuilderUtil.resolveTagName((String) attrKey, true, options, matcher,
                                xmlVersion);
                        attrStr.append(buildAttrPairStr(resolvedAttr, Js.toStr(value), isCurrentStopNode));
                    }
                } else if (Js.strictEquals(key, opt("textNodeName"))) {
                    Object newval = Js.call(opt("tagValueProcessor"), "this.options.tagValueProcessor", key,
                            Js.toStr(value));
                    val.append(Js.toStr(BuilderUtil.replaceEntitiesValue(newval, options)));
                } else {
                    val.append(buildLeaf(value, resolvedKey, level, matcher));
                }
            } else if (value instanceof JsArray array) {
                StringBuilder listTagVal = new StringBuilder();
                StringBuilder listTagAttr = new StringBuilder();
                boolean oneListGroup = Js.truthy(opt("oneListGroup"));
                for (int j = 0; j < array.size(); j++) {
                    Object item = array.get(j);
                    if (item == Js.UNDEFINED) {
                        continue;
                    } else if (item == null) {
                        val.append(indentate(level)).append('<').append(Js.toStr(resolvedKey))
                                .append(startsWithQuestion(resolvedKey) ? "?" : "/").append(tagEndChar);
                    } else if (Js.typeOf(item).equals("object")) {
                        if (oneListGroup) {
                            matcher.push(resolvedKey);
                            Built result = j2x(item, level + 1, matcher, xmlVersion);
                            matcher.pop();
                            listTagVal.append(result.val());
                            if (Js.truthy(opt("attributesGroupName")) && hasOwnProperty(item, opt("attributesGroupName"))) {
                                listTagAttr.append(result.attrStr());
                            }
                        } else {
                            listTagVal.append(processTextOrObjNode(item, resolvedKey, level, matcher, xmlVersion));
                        }
                    } else if (oneListGroup) {
                        Object textValue = Js.call(opt("tagValueProcessor"), "this.options.tagValueProcessor",
                                resolvedKey, item);
                        listTagVal.append(Js.toStr(BuilderUtil.replaceEntitiesValue(textValue, options)));
                    } else {
                        listTagVal.append(buildLeaf(item, resolvedKey, level, matcher));
                    }
                }
                String list = listTagVal.toString();
                if (oneListGroup) {
                    list = buildObjectNode(list, resolvedKey, listTagAttr.toString(), level);
                }
                val.append(list);
            } else if (Js.truthy(opt("attributesGroupName")) && Js.strictEquals(key, opt("attributesGroupName"))) {
                for (String attr : Js.keys(value)) {
                    Object resolvedAttr = BuilderUtil.resolveTagName(attr, true, options, matcher, xmlVersion);
                    attrStr.append(buildAttrPairStr(resolvedAttr, Js.toStr(Js.get(value, attr)), isCurrentStopNode));
                }
            } else {
                val.append(processTextOrObjNode(value, resolvedKey, level, matcher, xmlVersion));
            }
        }
        return new Built(attrStr.toString(), val.toString());
    }

    // item.hasOwnProperty(name), which fails when the item's own hasOwnProperty is not a function.
    private static boolean hasOwnProperty(Object item, Object name) {
        if (item instanceof JsObject object && object.hasOwn("hasOwnProperty")) {
            Object method = object.get("hasOwnProperty");
            return Js.truthy(Js.call(method, "item.hasOwnProperty", name));
        }
        return Js.hasOwn(item, Js.toStr(name));
    }

    // A primitive tag value: raw for a stop node, else through buildTextValNode.
    private String buildLeaf(Object value, Object resolvedKey, int level, Matcher matcher) {
        matcher.push(resolvedKey);
        boolean isStopNode = checkStopNode(matcher);
        matcher.pop();
        if (!isStopNode) {
            return buildTextValNode(value, resolvedKey, "", level);
        }
        String key = Js.toStr(resolvedKey);
        String textValue = Js.toStr(value);
        if (textValue.isEmpty()) {
            return indentate(level) + "<" + key + closeTag(resolvedKey) + tagEndChar;
        }
        return indentate(level) + "<" + key + ">" + textValue + "</" + key + tagEndChar;
    }

    private String buildAttrPairStr(Object attrName, Object value, boolean isStopNode) {
        Object val = value;
        if (!isStopNode) {
            val = Js.call(opt("attributeValueProcessor"), "this.options.attributeValueProcessor", attrName,
                    Js.toStr(val));
            val = BuilderUtil.replaceEntitiesValue(val, options);
        }
        if (Js.truthy(opt("suppressBooleanAttributes")) && Js.strictEquals(val, "true")) {
            return " " + Js.toStr(attrName);
        }
        return " " + Js.toStr(attrName) + "=\"" + BuilderUtil.escapeAttribute(val) + "\"";
    }

    private String processTextOrObjNode(Object object, Object key, int level, Matcher matcher, Object xmlVersion) {
        Object attrValues = extractAttributes(object);
        matcher.push(key, attrValues, null);
        if (checkStopNode(matcher)) {
            Object rawContent = buildRawContent(object);
            String attrStr = buildAttributesForStopNode(object);
            matcher.pop();
            return buildObjectNode(rawContent, key, attrStr, level);
        }
        Built result = j2x(object, level + 1, matcher, xmlVersion);
        matcher.pop();
        if (startsWithQuestion(key)) {
            return buildTextValNode("", key, result.attrStr(), level);
        }
        Object text = Js.get(object, Js.toStr(opt("textNodeName")));
        if (text != Js.UNDEFINED && Js.keys(object).size() == 1) {
            return buildTextValNode(text, key, result.attrStr(), level);
        }
        return buildObjectNode(result.val(), key, result.attrStr(), level);
    }

    private Object extractAttributes(Object obj) {
        if (!Js.truthy(obj) || !Js.typeOf(obj).equals("object")) {
            return null;
        }
        JsObject attrValues = new JsObject();
        boolean hasAttrs = false;
        Object groupName = opt("attributesGroupName");
        if (Js.truthy(groupName) && Js.truthy(Js.get(obj, Js.toStr(groupName)))) {
            Object attrGroup = Js.get(obj, Js.toStr(groupName));
            for (String attrKey : Js.forInKeys(attrGroup)) {
                String cleanKey = Js.startsWith(attrKey, opt("attributeNamePrefix"))
                        ? Js.substringFrom(attrKey, Js.get(opt("attributeNamePrefix"), "length")) : attrKey;
                attrValues.set(cleanKey, BuilderUtil.escapeAttribute(Js.get(attrGroup, attrKey)));
                hasAttrs = true;
            }
        } else {
            for (String key : Js.forInKeys(obj)) {
                Object attr = isAttribute(key);
                if (Js.truthy(attr)) {
                    attrValues.set((String) attr, BuilderUtil.escapeAttribute(Js.get(obj, key)));
                    hasAttrs = true;
                }
            }
        }
        return hasAttrs ? attrValues : null;
    }

    private Object buildRawContent(Object obj) {
        if (obj instanceof String) {
            return obj;
        }
        if (!Js.typeOf(obj).equals("object") || obj == null) {
            return Js.toStr(obj);
        }
        Object text = Js.get(obj, Js.toStr(opt("textNodeName")));
        if (text != Js.UNDEFINED) {
            return text;
        }
        StringBuilder content = new StringBuilder();
        for (String key : Js.forInKeys(obj)) {
            if (Js.truthy(isAttribute(key))) {
                continue;
            }
            if (Js.truthy(opt("attributesGroupName")) && Js.strictEquals(key, opt("attributesGroupName"))) {
                continue;
            }
            Object value = Js.get(obj, key);
            if (Js.strictEquals(key, opt("textNodeName"))) {
                content.append(Js.toStr(value));
            } else if (value instanceof JsArray array) {
                for (Object item : array) {
                    if (item instanceof String || item instanceof Double) {
                        content.append('<').append(key).append('>').append(Js.toStr(item)).append("</").append(key)
                                .append('>');
                    } else if (Js.typeOf(item).equals("object") && item != null) {
                        content.append(rawElement(key, item));
                    }
                }
            } else if (Js.typeOf(value).equals("object") && value != null) {
                content.append(rawElement(key, value));
            } else {
                content.append('<').append(key).append('>').append(Js.toStr(value)).append("</").append(key)
                        .append('>');
            }
        }
        return content.toString();
    }

    private String rawElement(String key, Object item) {
        Object nestedContent = buildRawContent(item);
        String nestedAttrs = buildAttributesForStopNode(item);
        if (Js.strictEquals(nestedContent, "")) {
            return "<" + key + nestedAttrs + "/>";
        }
        return "<" + key + nestedAttrs + ">" + Js.toStr(nestedContent) + "</" + key + ">";
    }

    private String buildAttributesForStopNode(Object obj) {
        if (!Js.truthy(obj) || !Js.typeOf(obj).equals("object")) {
            return "";
        }
        StringBuilder attrStr = new StringBuilder();
        boolean suppress = Js.truthy(opt("suppressBooleanAttributes"));
        Object groupName = opt("attributesGroupName");
        if (Js.truthy(groupName) && Js.truthy(Js.get(obj, Js.toStr(groupName)))) {
            Object attrGroup = Js.get(obj, Js.toStr(groupName));
            for (String attrKey : Js.forInKeys(attrGroup)) {
                String cleanKey = Js.startsWith(attrKey, opt("attributeNamePrefix"))
                        ? Js.substringFrom(attrKey, Js.get(opt("attributeNamePrefix"), "length")) : attrKey;
                appendRawAttribute(attrStr, cleanKey, Js.get(attrGroup, attrKey), suppress);
            }
        } else {
            for (String key : Js.forInKeys(obj)) {
                Object attr = isAttribute(key);
                if (Js.truthy(attr)) {
                    appendRawAttribute(attrStr, (String) attr, Js.get(obj, key), suppress);
                }
            }
        }
        return attrStr.toString();
    }

    private static void appendRawAttribute(StringBuilder attrStr, String name, Object val, boolean suppress) {
        if (Js.strictEquals(val, true) && suppress) {
            attrStr.append(' ').append(name);
        } else {
            attrStr.append(' ').append(name).append("=\"").append(Js.toStr(val)).append('"');
        }
    }

    private static boolean containsLessThan(Object val) {
        if (val instanceof String s) {
            return s.indexOf('<') >= 0;
        }
        if (val instanceof JsArray array) {
            return array.items().stream().anyMatch(item -> Js.strictEquals(item, "<"));
        }
        Js.call(Js.get(val, "indexOf"), "val.indexOf", "<");
        return false;
    }

    private String buildObjectNode(Object val, Object key, String attrStr, int level) {
        String name = Js.toStr(key);
        if (Js.strictEquals(val, "")) {
            if (name.startsWith("?")) {
                return indentate(level) + "<" + name + attrStr + "?" + tagEndChar;
            }
            return indentate(level) + "<" + name + attrStr + closeTag(key) + tagEndChar;
        } else if (name.startsWith("?")) {
            return indentate(level) + "<" + name + attrStr + "?" + tagEndChar;
        }
        String tagEndExp = "</" + name + tagEndChar;
        if (!containsLessThan(val)) {
            return indentate(level) + "<" + name + attrStr + ">" + Js.toStr(val) + tagEndExp;
        } else if (!Js.strictEquals(opt("commentPropName"), false) && Js.strictEquals(key, opt("commentPropName"))) {
            return indentate(level) + "<!--" + Js.toStr(val) + "-->" + newLine;
        }
        return indentate(level) + "<" + name + attrStr + tagEndChar + Js.toStr(val) + indentate(level) + tagEndExp;
    }

    private String closeTag(Object key) {
        Object unpairedTags = opt("unpairedTags");
        boolean unpaired;
        if (unpairedTags instanceof JsArray array) {
            unpaired = array.items().stream().anyMatch(tag -> Js.strictEquals(tag, key));
        } else if (unpairedTags instanceof String s) {
            unpaired = s.contains(Js.toStr(key));
        } else {
            Js.call(Js.get(unpairedTags, "indexOf"), "this.options.unpairedTags.indexOf", key);
            unpaired = false;
        }
        if (unpaired) {
            return Js.truthy(opt("suppressUnpairedNode")) ? "" : "/";
        } else if (Js.truthy(opt("suppressEmptyNode"))) {
            return "/";
        }
        return "></" + Js.toStr(key);
    }

    private String buildTextValNode(Object val, Object key, String attrStr, int level) {
        String name = Js.toStr(key);
        if (!Js.strictEquals(opt("cdataPropName"), false) && Js.strictEquals(key, opt("cdataPropName"))) {
            return indentate(level) + "<![CDATA[" + BuilderUtil.safeCdata(val) + "]]>" + newLine;
        } else if (!Js.strictEquals(opt("commentPropName"), false) && Js.strictEquals(key, opt("commentPropName"))) {
            return indentate(level) + "<!--" + BuilderUtil.safeComment(val) + "-->" + newLine;
        } else if (name.startsWith("?")) {
            return indentate(level) + "<" + name + attrStr + "?" + tagEndChar;
        }
        Object textValue = Js.call(opt("tagValueProcessor"), "this.options.tagValueProcessor", key, val);
        textValue = BuilderUtil.replaceEntitiesValue(textValue, options);
        if (Js.strictEquals(textValue, "")) {
            return indentate(level) + "<" + name + attrStr + closeTag(key) + tagEndChar;
        }
        return indentate(level) + "<" + name + attrStr + ">" + Js.toStr(textValue) + "</" + name + tagEndChar;
    }
}
