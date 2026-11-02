// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/xmlparser/OrderedObjParser.js, src/ignoreAttributes.js).

package io.github.naturalintelligence.fxp.parser;

import io.github.naturalintelligence.fxp.entities.Entities;
import io.github.naturalintelligence.fxp.entities.EntityDecoder;
import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsRegExp;
import io.github.naturalintelligence.fxp.js.JsString;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.pathexpr.ExpressionSet;
import io.github.naturalintelligence.fxp.pathexpr.Matcher;
import io.github.naturalintelligence.fxp.pathexpr.MatcherView;
import io.github.naturalintelligence.fxp.strnum.StrNum;
import io.github.naturalintelligence.fxp.unsafe.IsUnsafe;
import java.util.ArrayList;
import java.util.List;

/** Parses XML text into the ordered tree: a list of {@code {tag: children, ":@": attributes}} entries. */
final class OrderedObjParser {
    private static final String ROOT = "!xml";

    private final JsRegExp attrsRegex = JsRegExp.create("([^\\s=]+)\\s*(=\\s*(['\"])([\\s\\S]*?)\\3)?", "gm");
    private IsUnsafe isUnsafe;
    private final JsObject options;
    private final List<XmlNode> tagsNodeStack = new ArrayList<>();
    private final JsFunction ignoreAttributesFn;
    private final Object entityDecoder;
    private final Matcher matcher = new Matcher();
    private final MatcherView readonlyMatcher = matcher.readOnly();
    private final ExpressionSet stopNodeExpressionsSet = new ExpressionSet();
    private boolean isCurrentNodeStopNode;
    private boolean doctypeFound;

    OrderedObjParser(JsObject options, JsObject externalEntities) {
        this.options = options;
        this.ignoreAttributesFn = OptionsBuilder.ignoreAttributesFn(options.get("ignoreAttributes"));
        Object custom = options.get("entityDecoder");
        if (Js.truthy(custom)) {
            this.entityDecoder = custom;
        } else {
            Object htmlEntities = options.get("htmlEntities");
            Object namedEntities = Entities.xml();
            if (Js.typeOf(htmlEntities).equals("object")) {
                namedEntities = htmlEntities;
            } else if (Js.strictEquals(htmlEntities, true)) {
                JsObject html = new JsObject();
                Js.spreadInto(html, Entities.commonHtml());
                Js.spreadInto(html, Entities.currency());
                namedEntities = html;
            }
            JsObject merged = new JsObject();
            Js.spreadInto(merged, namedEntities);
            Js.spreadInto(merged, externalEntities);
            JsObject processEntities = (JsObject) options.get("processEntities");
            JsObject limit = new JsObject();
            limit.set("maxTotalExpansions", processEntities.get("maxTotalExpansions"));
            limit.set("maxExpandedLength", processEntities.get("maxExpandedLength"));
            limit.set("applyLimitsTo", processEntities.get("appliesTo"));
            JsObject decoderOptions = new JsObject();
            decoderOptions.set("namedEntities", merged);
            decoderOptions.set("numericAllowed", htmlEntities);
            decoderOptions.set("limit", limit);
            decoderOptions.set("onInputEntity", (JsFunction) args -> isUnsafeInput((String) args[1]) ? "block" : "allow");
            this.entityDecoder = new EntityDecoder(decoderOptions);
        }
        Object stopNodes = options.get("stopNodes");
        if (Js.truthy(stopNodes) && Js.greaterThan(Js.get(stopNodes, "length"), 0.0)) {
            double length = Js.toNumber(Js.get(stopNodes, "length"));
            for (int i = 0; i < length; i++) {
                Object stopNodeExp = Js.get(stopNodes, i);
                if (stopNodeExp instanceof String pattern) {
                    stopNodeExpressionsSet.add(new Expression(pattern));
                } else if (stopNodeExp instanceof Expression expression) {
                    stopNodeExpressionsSet.add(expression);
                }
            }
        }
    }

    Matcher matcher() {
        return matcher;
    }

    private boolean isUnsafeInput(String value) {
        if (isUnsafe == null) {
            isUnsafe = new IsUnsafe();
        }
        return isUnsafe.isUnsafeInHtmlOrXml(value);
    }

    MatcherView readonlyMatcher() {
        return readonlyMatcher;
    }

    private Object opt(String name) {
        return options.get(name);
    }

    private boolean flag(String name) {
        return Js.truthy(options.get(name));
    }

    private boolean unpaired(Object tagName) {
        return ((OptionsBuilder.UnpairedTags) options.get("unpairedTagsSet")).has(tagName);
    }

    private Object jPathFor(Object jPath) {
        return flag("jPath") ? Js.toStr(jPath) : jPath;
    }

    // ---- entity decoder calls (any object with these methods works, as in JavaScript) ----

    private Object decoderMethod(String name, Object... args) {
        return Js.call(Js.get(entityDecoder, name), "this.entityDecoder." + name, args);
    }

    private void decoderReset() {
        if (entityDecoder instanceof EntityDecoder decoder) {
            decoder.reset();
        } else {
            decoderMethod("reset");
        }
    }

    private void decoderSetXmlVersion(double version) {
        if (entityDecoder instanceof EntityDecoder decoder) {
            decoder.setXmlVersion(version);
        } else {
            decoderMethod("setXmlVersion", version);
        }
    }

    private void decoderAddInputEntities(JsObject entities) {
        if (entityDecoder instanceof EntityDecoder decoder) {
            decoder.addInputEntities(entities);
        } else {
            decoderMethod("addInputEntities", entities);
        }
    }

    private Object decoderDecode(Object val) {
        return entityDecoder instanceof EntityDecoder decoder ? decoder.decode(val) : decoderMethod("decode", val);
    }

    // ---- text and attribute values ----

    private Object parseTextData(Object value, Object tagName, MatcherView jPath, boolean dontTrim,
            boolean hasAttributes, boolean isLeafNode, boolean escapeEntities) {
        if (value == Js.UNDEFINED) {
            return Js.UNDEFINED;
        }
        Object val = value;
        if (flag("trimValues") && !dontTrim) {
            val = JsString.trim((String) val);
        }
        if (((String) val).isEmpty()) {
            return Js.UNDEFINED;
        }
        if (!escapeEntities) {
            val = replaceEntitiesValue(val, tagName, jPath);
        }
        Object newval = Js.call(opt("tagValueProcessor"), "options.tagValueProcessor", tagName, val,
                jPathFor(jPath), hasAttributes, isLeafNode);
        if (Js.isNullish(newval)) {
            return val;
        } else if (!Js.typeOf(newval).equals(Js.typeOf(val)) || !Js.strictEquals(newval, val)) {
            return newval;
        } else if (flag("trimValues")) {
            return parseValue(val, opt("parseTagValue"), opt("numberParseOptions"));
        }
        if (!(val instanceof String text)) {
            throw JsError.typeError("val.trim is not a function");
        }
        return JsString.trim(text).equals(text) ? parseValue(val, opt("parseTagValue"), opt("numberParseOptions"))
                : val;
    }

    static Object parseValue(Object val, Object shouldParse, Object numberParseOptions) {
        if (Js.truthy(shouldParse) && val instanceof String text) {
            String newval = JsString.trim(text);
            if (newval.equals("true")) {
                return true;
            } else if (newval.equals("false")) {
                return false;
            }
            return StrNum.toNumber(text, numberParseOptions);
        }
        return val == Js.UNDEFINED ? "" : val;
    }

    private String resolveNameSpace(String tagname) {
        if (flag("removeNSPrefix")) {
            String[] tags = tagname.split(":", -1);
            String prefix = tagname.startsWith("/") ? "/" : "";
            if (tags[0].equals("xmlns")) {
                return "";
            }
            if (tags.length == 2) {
                return prefix + tags[1];
            }
        }
        return tagname;
    }

    private Object buildAttributesMap(String attrStr, Matcher jPath, Object tagName, boolean force) {
        if (!force && Js.strictEquals(opt("ignoreAttributes"), true)) {
            return Js.UNDEFINED;
        }
        List<JsRegExp.Match> matches = attrsRegex.matchAll(attrStr);
        int len = matches.size();
        JsObject attrs = new JsObject();
        Object[] processedVals = new Object[len];
        boolean hasRawAttrs = false;
        JsObject rawAttrsForMatcher = new JsObject();
        for (int i = 0; i < len; i++) {
            String attrName = resolveNameSpace(matches.get(i).captures()[1]);
            String oldVal = matches.get(i).captures()[4];
            if (!attrName.isEmpty() && oldVal != null) {
                Object val = oldVal;
                if (flag("trimValues")) {
                    val = JsString.trim(oldVal);
                }
                val = replaceEntitiesValue(val, tagName, readonlyMatcher);
                processedVals[i] = val;
                rawAttrsForMatcher.set(attrName, val);
                hasRawAttrs = true;
            }
        }
        if (hasRawAttrs) {
            jPath.updateCurrent(rawAttrsForMatcher);
        }
        Object jPathStr = flag("jPath") ? jPath.toString() : readonlyMatcher;
        boolean hasAttrs = false;
        for (int i = 0; i < len; i++) {
            String attrName = resolveNameSpace(matches.get(i).captures()[1]);
            if (Js.truthy(ignoreAttributesFn.call(attrName, jPathStr))) {
                continue;
            }
            Object aName = Js.add(opt("attributeNamePrefix"), attrName);
            if (attrName.isEmpty()) {
                continue;
            }
            if (flag("transformAttributeName")) {
                aName = Js.call(opt("transformAttributeName"), "options.transformAttributeName", aName);
            }
            aName = sanitizeName(aName, options);
            String key = Js.toStr(aName);
            if (matches.get(i).captures()[4] != null) {
                Object oldVal = processedVals[i];
                Object newVal = Js.call(opt("attributeValueProcessor"), "options.attributeValueProcessor", attrName,
                        oldVal, jPathStr);
                if (Js.isNullish(newVal)) {
                    attrs.set(key, oldVal);
                } else if (!Js.typeOf(newVal).equals(Js.typeOf(oldVal)) || !Js.strictEquals(newVal, oldVal)) {
                    attrs.set(key, newVal);
                } else {
                    attrs.set(key, parseValue(oldVal, opt("parseAttributeValue"), opt("numberParseOptions")));
                }
                hasAttrs = true;
            } else if (flag("allowBooleanAttributes")) {
                attrs.set(key, true);
                hasAttrs = true;
            }
        }
        if (!hasAttrs) {
            return Js.UNDEFINED;
        }
        if (flag("attributesGroupName") && !flag("preserveOrder")) {
            JsObject attrCollection = new JsObject();
            attrCollection.set(Js.toStr(opt("attributesGroupName")), attrs);
            return attrCollection;
        }
        return attrs;
    }

    private Object replaceEntitiesValue(Object val, Object tagName, MatcherView jPath) {
        JsObject entityConfig = (JsObject) opt("processEntities");
        if (!Js.truthy(entityConfig.get("enabled"))) {
            return val;
        }
        Object allowedTags = entityConfig.get("allowedTags");
        if (Js.truthy(allowedTags)) {
            Object jPathOrMatcher = jPathFor(jPath);
            boolean allowed;
            if (allowedTags instanceof JsArray tags) {
                allowed = tags.items().stream().anyMatch(tag -> Js.strictEquals(tag, tagName));
            } else {
                allowed = Js.truthy(Js.call(allowedTags, "entityConfig.allowedTags", tagName, jPathOrMatcher));
            }
            if (!allowed) {
                return val;
            }
        }
        Object tagFilter = entityConfig.get("tagFilter");
        if (Js.truthy(tagFilter)) {
            Object jPathOrMatcher = jPathFor(jPath);
            if (!Js.truthy(Js.call(tagFilter, "entityConfig.tagFilter", tagName, jPathOrMatcher))) {
                return val;
            }
        }
        return decoderDecode(val);
    }

    private String saveTextToParentTag(String textData, XmlNode parentNode, MatcherView jPath, Boolean leaf) {
        if (!textData.isEmpty()) {
            boolean isLeafNode = leaf == null ? parentNode.child.size() == 0 : leaf;
            boolean hasAttributes = Js.truthy(parentNode.attributes) && Js.keys(parentNode.attributes).size() != 0;
            Object text = parseTextData(textData, parentNode.tagname, jPath, false, hasAttributes, isLeafNode, false);
            if (text != Js.UNDEFINED && !Js.strictEquals(text, "")) {
                parentNode.add(opt("textNodeName"), text);
            }
        }
        return "";
    }

    private void addChild(XmlNode currentNode, XmlNode childNode, MatcherView jPath, int startIndex) {
        Object start = flag("captureMetaData") ? (Object) (double) startIndex : Js.UNDEFINED;
        Object result = Js.call(opt("updateTag"), "this.options.updateTag", childNode.tagname, jPathFor(jPath),
                childNode.attributes);
        if (Js.strictEquals(result, false)) {
            return;
        }
        if (result instanceof String name) {
            childNode.tagname = name;
        }
        currentNode.addChild(childNode, start);
    }

    // extractRawAttributes: its result is unused by the parser, but its prefix handling can throw.
    private void extractRawAttributes(Object prefixedAttrs) {
        Object attrs = flag("attributesGroupName") ? Js.get(prefixedAttrs, Js.toStr(opt("attributesGroupName")))
                : prefixedAttrs;
        if (!Js.truthy(attrs)) {
            return;
        }
        for (String key : Js.forInKeys(attrs)) {
            Node2Json.stripPrefix(key, opt("attributeNamePrefix"));
        }
    }

    private boolean isItStopNode() {
        return stopNodeExpressionsSet.size() != 0 && stopNodeExpressionsSet.matchesAny(matcher);
    }

    static Object sanitizeName(Object name, JsObject options) {
        if (OptionsBuilder.CRITICAL_PROPERTIES.contains(name)) {
            throw JsError.error("[SECURITY] Invalid name: \"" + name
                    + "\" is a reserved JavaScript keyword that could cause prototype pollution");
        } else if (OptionsBuilder.DANGEROUS_PROPERTY_NAMES.contains(name)) {
            return Js.call(options.get("onDangerousProperty"), "options.onDangerousProperty", name);
        }
        return name;
    }

    // transformTagName(fn, tagName, tagExp): returns {tagName, tagExp}.
    private Object[] transformTagName(Object tagName, Object tagExp) {
        Object fn = opt("transformTagName");
        Object name = tagName;
        Object exp = tagExp;
        if (Js.truthy(fn)) {
            Object newTagName = Js.call(fn, "fn", name);
            if (Js.strictEquals(exp, name)) {
                exp = newTagName;
            }
            name = newTagName;
        }
        return new Object[] {sanitizeName(name, options), exp};
    }

    // ---- reading tags ----

    private record TagExp(String data, int index) {
    }

    private static TagExp tagExpWithClosingIndex(String xmlData, int start, String closingChar) {
        int attrBoundary = 0;
        int len = xmlData.length();
        char close0 = closingChar.charAt(0);
        int close1 = closingChar.length() > 1 ? closingChar.charAt(1) : -1;
        StringBuilder result = new StringBuilder();
        int segmentStart = start;
        for (int index = start; index < len; index++) {
            char code = xmlData.charAt(index);
            if (attrBoundary != 0) {
                if (code == attrBoundary) {
                    attrBoundary = 0;
                }
            } else if (code == '"' || code == '\'') {
                attrBoundary = code;
            } else if (code == close0) {
                if (close1 != -1) {
                    if (index + 1 < len && xmlData.charAt(index + 1) == close1) {
                        result.append(xmlData, segmentStart, index);
                        return new TagExp(result.toString(), index);
                    }
                } else {
                    result.append(xmlData, segmentStart, index);
                    return new TagExp(result.toString(), index);
                }
            } else if (code == '\t') {
                result.append(xmlData, segmentStart, index).append(' ');
                segmentStart = index + 1;
            }
        }
        return null;
    }

    private static int findClosingIndex(String xmlData, String str, int i, String errMsg) {
        int closingIndex = xmlData.indexOf(str, Math.max(i, 0));
        if (closingIndex == -1) {
            throw JsError.error(errMsg);
        }
        return closingIndex + str.length() - 1;
    }

    private record TagData(String tagName, String tagExp, int closeIndex, boolean attrExpPresent,
            String rawTagName) {
    }

    private static TagData readTagExp(String xmlData, int i, boolean removeNSPrefix, String closingChar) {
        TagExp result = tagExpWithClosingIndex(xmlData, i + 1, closingChar);
        if (result == null) {
            return null;
        }
        String tagExp = result.data();
        int separatorIndex = JsString.searchWhiteSpace(tagExp);
        String tagName = tagExp;
        boolean attrExpPresent = true;
        if (separatorIndex != -1) {
            tagName = tagExp.substring(0, separatorIndex);
            tagExp = JsString.trimStart(tagExp.substring(separatorIndex + 1));
        }
        String rawTagName = tagName;
        if (removeNSPrefix) {
            int colonIndex = tagName.indexOf(':');
            if (colonIndex != -1) {
                tagName = tagName.substring(colonIndex + 1);
                attrExpPresent = !tagName.equals(JsString.substr(result.data(), colonIndex + 1));
            }
        }
        return new TagData(tagName, tagExp, result.index(), attrExpPresent, rawTagName);
    }

    private record StopNodeData(String tagContent, int index) {
    }

    private static StopNodeData readStopNodeData(String xmlData, String tagName, int start) {
        int openTagCount = 1;
        int len = xmlData.length();
        for (int i = start; i < len; i++) {
            if (xmlData.charAt(i) != '<') {
                continue;
            }
            int c1 = i + 1 < len ? xmlData.charAt(i + 1) : -1;
            if (c1 == '/') {
                int closeIndex = xmlData.indexOf('>', i);
                if (closeIndex == -1) {
                    throw JsError.error(tagName + " is not closed");
                }
                String closeTagName = JsString.trim(xmlData.substring(i + 2, Math.max(i + 2, closeIndex)));
                if (closeTagName.equals(tagName)) {
                    openTagCount--;
                    if (openTagCount == 0) {
                        return new StopNodeData(xmlData.substring(start, i), closeIndex);
                    }
                }
                i = closeIndex;
            } else if (c1 == '?') {
                i = findClosingIndex(xmlData, "?>", i + 1, "StopNode is not closed.");
            } else if (c1 == '!' && i + 3 < len && xmlData.charAt(i + 2) == '-' && xmlData.charAt(i + 3) == '-') {
                i = findClosingIndex(xmlData, "-->", i + 3, "StopNode is not closed.");
            } else if (c1 == '!' && i + 2 < len && xmlData.charAt(i + 2) == '[') {
                i = findClosingIndex(xmlData, "]]>", i, "StopNode is not closed.") - 2;
            } else {
                TagData tagData = readTagExp(xmlData, i, false, ">");
                if (tagData != null) {
                    String exp = tagData.tagExp();
                    if (tagData.tagName().equals(tagName) && (exp.isEmpty() || exp.charAt(exp.length() - 1) != '/')) {
                        openTagCount++;
                    }
                    i = tagData.closeIndex();
                }
            }
        }
        return null;
    }

    private static String extractNamespace(String rawTagName) {
        if (rawTagName.isEmpty()) {
            return null;
        }
        int colonIndex = rawTagName.indexOf(':');
        if (colonIndex > 0) {
            String ns = rawTagName.substring(0, colonIndex);
            if (!ns.equals("xmlns")) {
                return ns;
            }
        }
        return null;
    }

    private static boolean charIs(String data, int index, char c) {
        return index < data.length() && data.charAt(index) == c;
    }

    // ---- the main loop ----

    JsArray parseXml(String input) {
        String xmlData = input.replace("\r\n", "\n").replace('\r', '\n');
        XmlNode xmlObj = new XmlNode(ROOT);
        XmlNode currentNode = xmlObj;
        String textData = "";
        matcher.reset();
        decoderReset();
        doctypeFound = false;
        boolean captureMetaData = flag("captureMetaData");
        DocTypeReader docTypeReader = new DocTypeReader((JsObject) opt("processEntities"));
        int xmlLen = xmlData.length();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < xmlLen; i++) {
            char ch = xmlData.charAt(i);
            if (ch != '<') {
                text.append(ch);
                continue;
            }
            textData = textData + text;
            text.setLength(0);
            if (charIs(xmlData, i + 1, '/')) {
                int closeIndex = findClosingIndex(xmlData, ">", i, "Closing Tag is not closed.");
                String closing = JsString.trim(xmlData.substring(Math.min(i + 2, closeIndex), closeIndex));
                if (flag("removeNSPrefix")) {
                    int colonIndex = closing.indexOf(':');
                    if (colonIndex != -1) {
                        closing = closing.substring(colonIndex + 1);
                    }
                }
                Object tagName = transformTagName(closing, "")[0];
                textData = saveTextToParentTag(textData, currentNode, readonlyMatcher, null);
                Object lastTagName = matcher.getCurrentTag();
                if (Js.truthy(tagName) && unpaired(tagName)) {
                    throw JsError.error("Unpaired tag can not be used as closing tag: </" + Js.toStr(tagName) + ">");
                }
                if (Js.truthy(lastTagName) && unpaired(lastTagName)) {
                    matcher.pop();
                    if (!tagsNodeStack.isEmpty()) {
                        tagsNodeStack.remove(tagsNodeStack.size() - 1);
                    }
                }
                matcher.pop();
                isCurrentNodeStopNode = false;
                currentNode = tagsNodeStack.isEmpty() ? xmlObj : tagsNodeStack.remove(tagsNodeStack.size() - 1);
                if (captureMetaData) {
                    currentNode.addEndIndex(closeIndex + 1);
                }
                textData = "";
                i = closeIndex;
            } else if (charIs(xmlData, i + 1, '?')) {
                TagData tagData = readTagExp(xmlData, i, false, "?>");
                if (tagData == null) {
                    throw JsError.error("Pi Tag is not closed.");
                }
                textData = saveTextToParentTag(textData, currentNode, readonlyMatcher, null);
                Object attsMap = buildAttributesMap(tagData.tagExp(), matcher, tagData.tagName(), true);
                if (Js.truthy(attsMap)) {
                    Object ver = Js.get(attsMap, Js.toStr(Js.add(opt("attributeNamePrefix"), "version")));
                    double version = Js.toNumber(ver);
                    double xmlVersion = Double.isNaN(version) || version == 0 ? 1.0 : version;
                    decoderSetXmlVersion(xmlVersion);
                }
                boolean ignored = (flag("ignoreDeclaration") && tagData.tagName().equals("?xml"))
                        || flag("ignorePiTags");
                if (!ignored) {
                    XmlNode childNode = new XmlNode(tagData.tagName());
                    childNode.add(opt("textNodeName"), "");
                    if (!tagData.tagName().equals(tagData.tagExp()) && tagData.attrExpPresent()
                            && !Js.strictEquals(opt("ignoreAttributes"), true)) {
                        childNode.attributes = attsMap;
                    }
                    addChild(currentNode, childNode, readonlyMatcher, i);
                    if (captureMetaData) {
                        currentNode.addEndIndex(tagData.closeIndex() + 2);
                    }
                }
                i = tagData.closeIndex() + 1;
            } else if (charIs(xmlData, i + 1, '!') && charIs(xmlData, i + 2, '-') && charIs(xmlData, i + 3, '-')) {
                int endIndex = findClosingIndex(xmlData, "-->", i + 4, "Comment is not closed.");
                if (flag("commentPropName")) {
                    String comment = xmlData.substring(i + 4, Math.max(i + 4, endIndex - 2));
                    textData = saveTextToParentTag(textData, currentNode, readonlyMatcher, null);
                    JsObject entry = new JsObject();
                    entry.defineOwn(Js.toStr(opt("textNodeName")), comment);
                    currentNode.add(opt("commentPropName"), JsArray.of(entry));
                }
                i = endIndex;
            } else if (charIs(xmlData, i + 1, '!') && charIs(xmlData, i + 2, 'D')) {
                if (doctypeFound) {
                    throw JsError.error("Multiple DOCTYPE declarations found.");
                }
                doctypeFound = true;
                DocTypeReader.Result result = docTypeReader.readDocType(xmlData, i);
                decoderAddInputEntities(result.entities());
                i = result.index();
            } else if (charIs(xmlData, i + 1, '!') && charIs(xmlData, i + 2, '[')) {
                int closeIndex = findClosingIndex(xmlData, "]]>", i, "CDATA is not closed.") - 2;
                String tagExp = JsString.substring(xmlData, i + 9, closeIndex);
                textData = saveTextToParentTag(textData, currentNode, readonlyMatcher, null);
                Object val = parseTextData(tagExp, currentNode.tagname, readonlyMatcher, true, false, true, true);
                if (Js.isNullish(val)) {
                    val = "";
                }
                if (flag("cdataPropName")) {
                    JsObject entry = new JsObject();
                    entry.defineOwn(Js.toStr(opt("textNodeName")), tagExp);
                    currentNode.add(opt("cdataPropName"), JsArray.of(entry));
                } else {
                    currentNode.add(opt("textNodeName"), val);
                }
                i = closeIndex + 2;
            } else {
                OpenTag open = new OpenTag(xmlData, i, xmlObj, currentNode, textData);
                currentNode = open.currentNode;
                textData = open.textData;
                i = open.next;
            }
        }
        return xmlObj.child;
    }

    /** The handling of one opening tag; the loop continues with {@code next}, {@code currentNode}, {@code textData}. */
    private final class OpenTag {
        private XmlNode currentNode;
        private String textData;
        private int next;

        OpenTag(String xmlData, int i, XmlNode xmlObj, XmlNode node, String text) {
            currentNode = node;
            textData = text;
            TagData result = readTagExp(xmlData, i, flag("removeNSPrefix"), ">");
            if (result == null) {
                String context = xmlData.substring(Math.max(0, i - 50), Math.min(xmlData.length(), i + 50));
                throw JsError.error("readTagExp returned undefined at position " + i + ". Context: \"" + context
                        + "\"");
            }
            String rawTagName = result.rawTagName();
            boolean attrExpPresent = result.attrExpPresent();
            int closeIndex = result.closeIndex();
            Object[] transformed = transformTagName(result.tagName(), result.tagExp());
            Object tagName = transformed[0];
            Object tagExp = transformed[1];
            if (flag("strictReservedNames") && (Js.strictEquals(tagName, opt("commentPropName"))
                    || Js.strictEquals(tagName, opt("cdataPropName")) || Js.strictEquals(tagName, opt("textNodeName"))
                    || Js.strictEquals(tagName, opt("attributesGroupName")))) {
                throw JsError.error("Invalid tag name: " + Js.toStr(tagName));
            }
            if (!textData.isEmpty() && !Js.strictEquals(currentNode.tagname, ROOT)) {
                textData = saveTextToParentTag(textData, currentNode, readonlyMatcher, false);
            }
            if (unpaired(currentNode.tagname)) {
                currentNode = tagsNodeStack.isEmpty() ? null : tagsNodeStack.remove(tagsNodeStack.size() - 1);
                matcher.pop();
            }
            boolean isSelfClosing = false;
            if (tagExp instanceof String exp && !exp.isEmpty() && exp.lastIndexOf('/') == exp.length() - 1) {
                isSelfClosing = true;
                String name = Js.toStr(tagName);
                if (name.endsWith("/")) {
                    tagName = name.substring(0, name.length() - 1);
                    tagExp = tagName;
                } else {
                    tagExp = exp.substring(0, exp.length() - 1);
                }
                attrExpPresent = !Js.strictEquals(tagName, tagExp);
            }
            Object prefixedAttrs = null;
            String namespace = extractNamespace(rawTagName);
            boolean isRoot = Js.strictEquals(tagName, ROOT);
            if (!isRoot) {
                matcher.push(tagName, new JsObject(), namespace);
            }
            if (!Js.strictEquals(tagName, tagExp) && attrExpPresent) {
                Object attrs = buildAttributesMap(Js.toStr(tagExp), matcher, tagName, false);
                prefixedAttrs = Js.truthy(attrs) ? attrs : null;
                if (prefixedAttrs != null) {
                    extractRawAttributes(prefixedAttrs);
                }
            }
            if (!isRoot) {
                isCurrentNodeStopNode = isItStopNode();
            }
            int startIndex = i;
            boolean captureMetaData = flag("captureMetaData");
            if (isCurrentNodeStopNode) {
                String tagContent = "";
                int end;
                if (isSelfClosing || unpaired(tagName)) {
                    end = result.closeIndex();
                } else {
                    StopNodeData stop = readStopNodeData(xmlData, rawTagName, closeIndex + 1);
                    if (stop == null) {
                        throw JsError.error("Unexpected end of " + rawTagName);
                    }
                    end = stop.index();
                    tagContent = stop.tagContent();
                }
                XmlNode childNode = new XmlNode(tagName);
                if (prefixedAttrs != null) {
                    childNode.attributes = prefixedAttrs;
                }
                childNode.add(opt("textNodeName"), tagContent);
                matcher.pop();
                isCurrentNodeStopNode = false;
                addChild(currentNode, childNode, readonlyMatcher, startIndex);
                if (captureMetaData) {
                    currentNode.addEndIndex(end + 1);
                }
                next = end;
                return;
            }
            if (isSelfClosing) {
                tagName = transformTagName(tagName, tagExp)[0];
                XmlNode childNode = new XmlNode(tagName);
                if (prefixedAttrs != null) {
                    childNode.attributes = prefixedAttrs;
                }
                addChild(currentNode, childNode, readonlyMatcher, startIndex);
                if (captureMetaData) {
                    currentNode.addEndIndex(closeIndex + 1);
                }
                matcher.pop();
                isCurrentNodeStopNode = false;
            } else if (unpaired(tagName)) {
                XmlNode childNode = new XmlNode(tagName);
                if (prefixedAttrs != null) {
                    childNode.attributes = prefixedAttrs;
                }
                addChild(currentNode, childNode, readonlyMatcher, startIndex);
                if (captureMetaData) {
                    currentNode.addEndIndex(result.closeIndex() + 1);
                }
                matcher.pop();
                isCurrentNodeStopNode = false;
                next = result.closeIndex();
                return;
            } else {
                XmlNode childNode = new XmlNode(tagName);
                if (Js.greaterThan((double) tagsNodeStack.size(), opt("maxNestedTags"))) {
                    throw JsError.error("Maximum nested tags exceeded");
                }
                tagsNodeStack.add(currentNode);
                if (prefixedAttrs != null) {
                    childNode.attributes = prefixedAttrs;
                }
                addChild(currentNode, childNode, readonlyMatcher, startIndex);
                currentNode = childNode;
            }
            textData = "";
            next = closeIndex;
        }
    }
}
