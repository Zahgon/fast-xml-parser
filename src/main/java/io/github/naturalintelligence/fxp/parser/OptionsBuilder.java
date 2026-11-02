// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)
// Ported to Java from fast-xml-parser 5.11.2 (src/xmlparser/OptionsBuilder.js, src/util.js).

package io.github.naturalintelligence.fxp.parser;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsRegExp;
import io.github.naturalintelligence.fxp.js.JsString;
import java.util.ArrayList;
import java.util.List;

/** The parser's default options and {@code buildOptions}. */
public final class OptionsBuilder {
    /** Property names that may be renamed by {@code onDangerousProperty}. */
    static final List<String> DANGEROUS_PROPERTY_NAMES = List.of("hasOwnProperty", "toString", "valueOf",
            "__defineGetter__", "__defineSetter__", "__lookupGetter__", "__lookupSetter__");

    /** Property names that are always rejected. */
    static final List<String> CRITICAL_PROPERTIES = List.of("__proto__", "constructor", "prototype");

    private static final String[] PROPERTY_NAME_OPTIONS = {"attributeNamePrefix", "attributesGroupName",
        "textNodeName", "cdataPropName", "commentPropName"};

    private OptionsBuilder() {
    }

    /**
     * A function's argument as JavaScript sees it.
     *
     * @param args the call's arguments
     * @param index the position
     * @return the argument, or {@code undefined} past the end
     */
    public static Object arg(Object[] args, int index) {
        return index < args.length ? args[index] : Js.UNDEFINED;
    }

    /**
     * {@code getIgnoreAttributesFn(ignoreAttributes)}, for the parser and the builder.
     *
     * @param ignoreAttributes the option's value: a function, a list of names and expressions, or anything else
     * @return the predicate that decides whether an attribute is ignored
     */
    public static JsFunction ignoreAttributesFn(Object ignoreAttributes) {
        if (ignoreAttributes instanceof JsFunction function) {
            return function;
        }
        if (ignoreAttributes instanceof JsArray patterns) {
            return args -> {
                Object attrName = arg(args, 0);
                for (Object pattern : patterns) {
                    if (pattern instanceof String && Js.strictEquals(attrName, pattern)) {
                        return true;
                    }
                    if (pattern instanceof JsRegExp regexp && regexp.test(Js.toStr(attrName))) {
                        return true;
                    }
                }
                return Js.UNDEFINED;
            };
        }
        return args -> false;
    }

    /** The default {@code onDangerousProperty}: prefixes a dangerous name with "__". */
    static final JsFunction DEFAULT_ON_DANGEROUS_PROPERTY = args -> {
        Object name = arg(args, 0);
        return DANGEROUS_PROPERTY_NAMES.contains(name) ? "__" + name : name;
    };

    private static JsObject defaultOptions() {
        JsObject numberParseOptions = new JsObject();
        numberParseOptions.set("hex", true);
        numberParseOptions.set("leadingZeros", true);
        numberParseOptions.set("eNotation", true);
        numberParseOptions.set("unicode", false);
        JsObject defaults = new JsObject();
        defaults.set("preserveOrder", false);
        defaults.set("attributeNamePrefix", "@_");
        defaults.set("attributesGroupName", false);
        defaults.set("textNodeName", "#text");
        defaults.set("ignoreAttributes", true);
        defaults.set("removeNSPrefix", false);
        defaults.set("allowBooleanAttributes", false);
        defaults.set("parseTagValue", true);
        defaults.set("parseAttributeValue", false);
        defaults.set("trimValues", true);
        defaults.set("cdataPropName", false);
        defaults.set("numberParseOptions", numberParseOptions);
        defaults.set("tagValueProcessor", (JsFunction) args -> arg(args, 1));
        defaults.set("attributeValueProcessor", (JsFunction) args -> arg(args, 1));
        defaults.set("stopNodes", new JsArray());
        defaults.set("alwaysCreateTextNode", false);
        defaults.set("isArray", (JsFunction) args -> false);
        defaults.set("commentPropName", false);
        defaults.set("unpairedTags", new JsArray());
        defaults.set("processEntities", true);
        defaults.set("htmlEntities", false);
        defaults.set("entityDecoder", null);
        defaults.set("ignoreDeclaration", false);
        defaults.set("ignorePiTags", false);
        defaults.set("transformTagName", false);
        defaults.set("transformAttributeName", false);
        defaults.set("updateTag", (JsFunction) args -> arg(args, 0));
        defaults.set("captureMetaData", false);
        defaults.set("maxNestedTags", 100.0);
        defaults.set("strictReservedNames", true);
        defaults.set("jPath", true);
        defaults.set("onDangerousProperty", DEFAULT_ON_DANGEROUS_PROPERTY);
        return defaults;
    }

    private static void validatePropertyName(Object propertyName, String optionName) {
        if (!(propertyName instanceof String name)) {
            return;
        }
        String normalized = JsString.toLowerCase(name);
        boolean reserved = DANGEROUS_PROPERTY_NAMES.stream().anyMatch(d -> normalized.equals(JsString.toLowerCase(d)))
                || CRITICAL_PROPERTIES.stream().anyMatch(d -> normalized.equals(JsString.toLowerCase(d)));
        if (reserved) {
            throw JsError.error("[SECURITY] Invalid " + optionName + ": \"" + name
                    + "\" is a reserved JavaScript keyword that could cause prototype pollution");
        }
    }

    private static Object orDefault(Object value, double fallback) {
        return Js.isNullish(value) ? (Object) fallback : value;
    }

    private static JsObject normalizeProcessEntities(Object value) {
        JsObject config = new JsObject();
        if (value instanceof Boolean enabled) {
            config.set("enabled", enabled);
            config.set("maxEntitySize", 10000.0);
            config.set("maxExpansionDepth", 10000.0);
            config.set("maxTotalExpansions", Double.POSITIVE_INFINITY);
            config.set("maxExpandedLength", 100000.0);
            config.set("maxEntityCount", 1000.0);
            config.set("allowedTags", null);
            config.set("tagFilter", null);
            config.set("appliesTo", "all");
            return config;
        }
        if (Js.isObjectValue(value)) {
            config.set("enabled", !Js.strictEquals(Js.get(value, "enabled"), false));
            config.set("maxEntitySize", Js.max(1.0, orDefault(Js.get(value, "maxEntitySize"), 10000)));
            config.set("maxExpansionDepth", Js.max(1.0, orDefault(Js.get(value, "maxExpansionDepth"), 10000)));
            config.set("maxTotalExpansions", Js.max(1.0, orDefault(Js.get(value, "maxTotalExpansions"),
                    Double.POSITIVE_INFINITY)));
            config.set("maxExpandedLength", Js.max(1.0, orDefault(Js.get(value, "maxExpandedLength"), 100000)));
            config.set("maxEntityCount", Js.max(1.0, orDefault(Js.get(value, "maxEntityCount"), 1000)));
            Object allowedTags = Js.get(value, "allowedTags");
            config.set("allowedTags", Js.isNullish(allowedTags) ? null : allowedTags);
            Object tagFilter = Js.get(value, "tagFilter");
            config.set("tagFilter", Js.isNullish(tagFilter) ? null : tagFilter);
            Object appliesTo = Js.get(value, "appliesTo");
            config.set("appliesTo", Js.isNullish(appliesTo) ? "all" : appliesTo);
            return config;
        }
        return normalizeProcessEntities(true);
    }

    /**
     * {@code buildOptions(options)}: the defaults overlaid with the given options, validated and normalised.
     *
     * @param options the user options (any value)
     * @return the built options
     * @throws JsError when a property-name option is a reserved name
     */
    public static JsObject build(Object options) {
        JsObject built = defaultOptions();
        Js.assign(built, options);
        for (String name : PROPERTY_NAME_OPTIONS) {
            Object value = built.get(name);
            if (Js.truthy(value)) {
                validatePropertyName(value, name);
            }
        }
        if (built.get("onDangerousProperty") == null) {
            built.set("onDangerousProperty", DEFAULT_ON_DANGEROUS_PROPERTY);
        }
        built.set("processEntities", normalizeProcessEntities(built.get("processEntities")));
        List<Object> unpaired = new ArrayList<>();
        Js.iterate(built.get("unpairedTags"), unpaired::add);
        built.set("unpairedTagsSet", new UnpairedTags(unpaired));
        Object stopNodes = built.get("stopNodes");
        if (Js.truthy(stopNodes) && stopNodes instanceof JsArray array) {
            JsArray mapped = new JsArray();
            for (Object node : array) {
                if (node instanceof String s && s.startsWith("*.")) {
                    mapped.push(".." + s.substring(2));
                } else {
                    mapped.push(node);
                }
            }
            built.set("stopNodes", mapped);
        }
        return built;
    }

    /** {@code new Set(unpairedTags)}: membership by SameValueZero. */
    static final class UnpairedTags {
        private final List<Object> members;

        UnpairedTags(List<Object> members) {
            this.members = members;
        }

        boolean has(Object value) {
            for (Object member : members) {
                if (Js.strictEquals(member, value)
                        || (member instanceof Double a && value instanceof Double b && a.isNaN() && b.isNaN())) {
                    return true;
                }
            }
            return false;
        }
    }
}
