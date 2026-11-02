// SPDX-License-Identifier: MIT
// Author: Amit Gupta (https://solothought.com), MIT License
// Ported to Java from @nodable/entities 3.1.0 (src/EntityDecoder.js).

package io.github.naturalintelligence.fxp.entities;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsHost;
import io.github.naturalintelligence.fxp.js.JsNumber;
import io.github.naturalintelligence.fxp.js.JsObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-pass entity replacer: named entities from the input (DOCTYPE), external and base maps, numeric character
 * references classified by XML version, remove/leave lists and expansion limits counted per entity tier.
 */
public final class EntityDecoder implements JsHost {
    private static final String TIER_EXTERNAL = "external";
    private static final String TIER_BASE = "base";
    private static final String TIER_ALL = "all";
    private static final double ALLOW = 0;
    private static final double LEAVE = 1;
    private static final double REMOVE = 2;
    private static final double THROW = 3;
    private static final String SPECIAL_CHARS = "!?\\/[]$%{}^&*()<>|+";

    private final Object maxTotalExpansions;
    private final Object maxExpandedLength;
    private final JsFunction postCheck;
    private final List<Object> limitTiers;
    private final Object numericAllowed;
    private final JsObject baseMap;
    private final List<Object> removeSet;
    private final List<Object> leaveSet;
    private final JsFunction onExternalEntity;
    private final JsFunction onInputEntity;
    private final double ncrOnLevel;
    private final double ncrNullLevel;
    private double ncrXmlVersion;
    private JsObject externalMap = JsObject.withNullPrototype();
    private JsObject inputMap = JsObject.withNullPrototype();
    private double totalExpansions;
    private double expandedLength;

    /**
     * {@code new EntityDecoder(options)}.
     *
     * @param options the decoder options (undefined for the defaults)
     */
    public EntityDecoder(Object options) {
        Object opts = options == Js.UNDEFINED ? new JsObject() : options;
        Object limitOption = Js.get(opts, "limit");
        Object limit = Js.truthy(limitOption) ? limitOption : new JsObject();
        Object total = Js.get(limit, "maxTotalExpansions");
        maxTotalExpansions = Js.truthy(total) ? total : (Object) 0.0;
        Object length = Js.get(limit, "maxExpandedLength");
        maxExpandedLength = Js.truthy(length) ? length : (Object) 0.0;
        Object check = Js.get(opts, "postCheck");
        postCheck = check instanceof JsFunction function ? function : args -> args[0];
        Object applyLimitsTo = Js.get(limit, "applyLimitsTo");
        limitTiers = parseLimitTiers(Js.isNullish(applyLimitsTo) ? TIER_EXTERNAL : applyLimitsTo);
        Object numeric = Js.get(opts, "numericAllowed");
        numericAllowed = Js.isNullish(numeric) ? Boolean.TRUE : numeric;
        Object named = Js.get(opts, "namedEntities");
        baseMap = mergeEntityMaps(Entities.xml(), Js.truthy(named) ? named : null);
        removeSet = listOption(Js.get(opts, "remove"));
        leaveSet = listOption(Js.get(opts, "leave"));
        Object ncr = Js.get(opts, "ncr");
        if (!Js.truthy(ncr)) {
            ncrXmlVersion = 1.0;
            ncrOnLevel = ALLOW;
            ncrNullLevel = REMOVE;
        } else {
            ncrXmlVersion = Js.strictEquals(Js.get(ncr, "xmlVersion"), 1.1) ? 1.1 : 1.0;
            Object onLevel = level(Js.get(ncr, "onNCR"));
            ncrOnLevel = Js.isNullish(onLevel) ? ALLOW : Js.toNumber(onLevel);
            Object nullLevel = level(Js.get(ncr, "nullNCR"));
            ncrNullLevel = Js.max(Js.isNullish(nullLevel) ? REMOVE : nullLevel, REMOVE);
        }
        Object external = Js.get(opts, "onExternalEntity");
        onExternalEntity = external instanceof JsFunction function ? function : null;
        Object input = Js.get(opts, "onInputEntity");
        onInputEntity = input instanceof JsFunction function ? function : null;
    }

    // NCR_LEVEL[name]: a level, an Object.prototype member, or undefined.
    private static Object level(Object name) {
        JsObject levels = new JsObject();
        levels.defineOwn("allow", ALLOW);
        levels.defineOwn("leave", LEAVE);
        levels.defineOwn("remove", REMOVE);
        levels.defineOwn("throw", THROW);
        return levels.get(Js.toStr(name));
    }

    private static List<Object> listOption(Object value) {
        List<Object> list = new ArrayList<>();
        if (Js.truthy(value) && value instanceof JsArray array) {
            list.addAll(array.items());
        }
        return list;
    }

    private static boolean contains(List<Object> set, String token) {
        return set.stream().anyMatch(member -> token.equals(member));
    }

    private static List<Object> parseLimitTiers(Object raw) {
        List<Object> tiers = new ArrayList<>();
        if (!Js.truthy(raw) || TIER_EXTERNAL.equals(raw)) {
            tiers.add(TIER_EXTERNAL);
        } else if (TIER_ALL.equals(raw)) {
            tiers.add(TIER_ALL);
        } else if (TIER_BASE.equals(raw)) {
            tiers.add(TIER_BASE);
        } else if (raw instanceof JsArray array) {
            tiers.addAll(array.items());
        } else {
            tiers.add(TIER_EXTERNAL);
        }
        return tiers;
    }

    private static void validateEntityName(String name) {
        if (name.startsWith("#")) {
            throw JsError.error("[EntityReplacer] Invalid character '#' in entity name: \"" + name + "\"");
        }
        name.codePoints().forEach(cp -> {
            if (cp < 128 && SPECIAL_CHARS.indexOf(cp) >= 0) {
                throw JsError.error("[EntityReplacer] Invalid character '" + (char) cp + "' in entity name: \""
                        + name + "\"");
            }
        });
    }

    private static JsObject mergeEntityMaps(Object... maps) {
        JsObject out = JsObject.withNullPrototype();
        for (Object map : maps) {
            if (!Js.truthy(map)) {
                continue;
            }
            for (String key : Js.keys(map)) {
                Object raw = Js.get(map, key);
                if (raw instanceof String) {
                    out.set(key, raw);
                } else if (Js.truthy(raw) && Js.isObjectValue(raw) && Js.get(raw, "val") != Js.UNDEFINED) {
                    Object val = Js.get(raw, "val");
                    if (val instanceof String) {
                        out.set(key, val);
                    }
                }
            }
        }
        return out;
    }

    private boolean applyRegistrationHook(JsFunction hook, String name, Object value, String context) {
        if (hook == null) {
            return true;
        }
        Object action = hook.call(name, value);
        if ("block".equals(action)) {
            return false;
        }
        if ("throw".equals(action)) {
            throw JsError.error("[EntityDecoder] Registration of " + context + " entity \"&" + name
                    + ";\" was rejected by hook");
        }
        return true;
    }

    private JsObject filtered(Object map, JsFunction hook, String context) {
        JsObject flat = mergeEntityMaps(map);
        if (hook == null) {
            return flat;
        }
        JsObject kept = JsObject.withNullPrototype();
        for (String name : flat.keys()) {
            Object value = flat.get(name);
            if (applyRegistrationHook(hook, name, value, context)) {
                kept.set(name, value);
            }
        }
        return kept;
    }

    /**
     * {@code setExternalEntities(map)}: replaces the persistent external entities.
     *
     * @param map the entities
     */
    public void setExternalEntities(Object map) {
        if (Js.truthy(map)) {
            for (String key : Js.keys(map)) {
                validateEntityName(key);
            }
        }
        externalMap = filtered(map, onExternalEntity, "external");
    }

    /**
     * {@code addInputEntities(map)}: the DOCTYPE entities of the current document; resets the counters.
     *
     * @param map the entities
     */
    public void addInputEntities(Object map) {
        totalExpansions = 0;
        expandedLength = 0;
        inputMap = filtered(map, onInputEntity, "input");
    }

    /** {@code reset()}: forgets the input entities and resets the counters. */
    public void reset() {
        inputMap = JsObject.withNullPrototype();
        totalExpansions = 0;
        expandedLength = 0;
    }

    /**
     * {@code setXmlVersion(version)}.
     *
     * @param version the document's XML version
     */
    public void setXmlVersion(double version) {
        ncrXmlVersion = version == 1.1 ? 1.1 : 1.0;
    }

    /**
     * {@code decode(str)}: replaces every recognised entity reference.
     *
     * @param value the text (other values are returned as they are)
     * @return the decoded text, after the postCheck hook
     */
    public Object decode(Object value) {
        if (!(value instanceof String str) || str.isEmpty() || str.indexOf('&') < 0) {
            return value;
        }
        StringBuilder chunks = new StringBuilder();
        boolean replaced = false;
        int len = str.length();
        int last = 0;
        int i = 0;
        boolean limitExpansions = Js.greaterThan(maxTotalExpansions, 0.0);
        boolean limitLength = Js.greaterThan(maxExpandedLength, 0.0);
        while (i < len) {
            if (str.charAt(i) != '&') {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < len && str.charAt(j) != ';' && (j - i) <= 32) {
                j++;
            }
            if (j >= len || str.charAt(j) != ';') {
                i++;
                continue;
            }
            String token = str.substring(i + 1, j);
            if (token.isEmpty()) {
                i++;
                continue;
            }
            String replacement;
            String tier;
            if (contains(removeSet, token)) {
                replacement = "";
                tier = TIER_EXTERNAL;
            } else if (contains(leaveSet, token)) {
                i++;
                continue;
            } else if (token.charAt(0) == '#') {
                replacement = resolveNcr(token);
                if (replacement == null) {
                    i++;
                    continue;
                }
                tier = TIER_BASE;
            } else if (inputMap.hasOwn(token)) {
                replacement = (String) inputMap.get(token);
                tier = TIER_EXTERNAL;
            } else if (externalMap.hasOwn(token)) {
                replacement = (String) externalMap.get(token);
                tier = TIER_EXTERNAL;
            } else if (baseMap.hasOwn(token)) {
                replacement = (String) baseMap.get(token);
                tier = TIER_BASE;
            } else {
                i++;
                continue;
            }
            chunks.append(str, last, i).append(replacement);
            replaced = true;
            last = j + 1;
            i = last;
            if ((limitExpansions || limitLength) && tierCounts(tier)) {
                if (limitExpansions) {
                    totalExpansions++;
                    if (Js.greaterThan(totalExpansions, maxTotalExpansions)) {
                        throw JsError.error("[EntityReplacer] Entity expansion count limit exceeded: "
                                + JsNumber.toString(totalExpansions) + " > " + Js.toStr(maxTotalExpansions));
                    }
                }
                if (limitLength) {
                    int delta = replacement.length() - (token.length() + 2);
                    if (delta > 0) {
                        expandedLength += delta;
                        if (Js.greaterThan(expandedLength, maxExpandedLength)) {
                            throw JsError.error("[EntityReplacer] Expanded content length limit exceeded: "
                                    + JsNumber.toString(expandedLength) + " > " + Js.toStr(maxExpandedLength));
                        }
                    }
                }
            }
        }
        if (last < len) {
            chunks.append(str, last, len);
        }
        String result = replaced ? chunks.toString() : str;
        return postCheck.call(result, str);
    }

    private boolean tierCounts(String tier) {
        return limitTiers.contains(TIER_ALL) || limitTiers.contains(tier);
    }

    private double classifyNcr(double cp) {
        if (cp == 0) {
            return ncrNullLevel;
        }
        if (cp >= 0xD800 && cp <= 0xDFFF) {
            return REMOVE;
        }
        if (ncrXmlVersion == 1.0 && cp >= 0x01 && cp <= 0x1F && cp != 0x09 && cp != 0x0A && cp != 0x0D) {
            return REMOVE;
        }
        return -1;
    }

    // The replacement of a numeric reference, "" to remove it, null to leave it as it is.
    private String resolveNcr(String token) {
        char second = token.length() > 1 ? token.charAt(1) : 0;
        double cp = second == 'x' || second == 'X' ? JsNumber.parseInt(token.substring(2), 16)
                : JsNumber.parseInt(token.substring(1), 10);
        if (Double.isNaN(cp) || cp < 0 || cp > 0x10FFFF) {
            return null;
        }
        double minimum = classifyNcr(cp);
        if (!Js.truthy(numericAllowed) && minimum < REMOVE) {
            return null;
        }
        double effective = minimum == -1 ? ncrOnLevel : Js.max(ncrOnLevel, minimum);
        if (effective == REMOVE) {
            return "";
        }
        if (effective == LEAVE) {
            return null;
        }
        if (effective == THROW) {
            String hex = Long.toHexString((long) cp).toUpperCase();
            throw JsError.error("[EntityDecoder] Prohibited numeric character reference &" + token + "; (U+"
                    + "0".repeat(Math.max(0, 4 - hex.length())) + hex + ")");
        }
        return new String(Character.toChars((int) cp));
    }
}
