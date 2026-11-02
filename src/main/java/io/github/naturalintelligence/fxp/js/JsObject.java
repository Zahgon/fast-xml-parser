// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JavaScript object: string-keyed properties in JavaScript's key order (array-index keys first, ascending, then the
 * other keys in insertion order), either inheriting from {@code Object.prototype} or created with a null prototype,
 * plus the slot the parser's metadata symbol is stored in.
 */
public final class JsObject {
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final boolean nullPrototype;
    private int indexKeyCount;
    private Object metadata = Undefined.INSTANCE;

    /** Creates an empty plain object, {@code {}}. */
    public JsObject() {
        this(false);
    }

    private JsObject(boolean nullPrototype) {
        this.nullPrototype = nullPrototype;
    }

    /**
     * {@code Object.create(null)}.
     *
     * @return an empty object without a prototype
     */
    public static JsObject withNullPrototype() {
        return new JsObject(true);
    }

    /**
     * Whether a key is an array index, the keys JavaScript orders first.
     *
     * @param key a property key
     * @return true for the canonical decimal form of an integer from 0 to 2^32 - 2
     */
    public static boolean isArrayIndex(String key) {
        int length = key.length();
        if (length == 0 || length > 10 || (length > 1 && key.charAt(0) == '0')) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return Long.parseLong(key) <= 4294967294L;
    }

    /**
     * {@code object[key]}: an own property, else an {@code Object.prototype} member, else undefined.
     *
     * @param key the key
     * @return the value
     */
    public Object get(String key) {
        Object value = properties.get(key);
        if (value != null || properties.containsKey(key)) {
            return value;
        }
        if (!nullPrototype) {
            Object inherited = ObjectPrototype.member(key);
            if (inherited != null) {
                return inherited;
            }
        }
        return Undefined.INSTANCE;
    }

    /**
     * {@code Object.prototype.hasOwnProperty.call(object, key)}.
     *
     * @param key the key
     * @return whether the object has that own property
     */
    public boolean hasOwn(String key) {
        return properties.containsKey(key);
    }

    /**
     * {@code key in object}.
     *
     * @param key the key
     * @return whether the object has or inherits the property
     */
    public boolean has(String key) {
        return properties.containsKey(key) || (!nullPrototype && ObjectPrototype.member(key) != null);
    }

    /**
     * {@code object[key] = value}. Assigning {@code __proto__} on an object that inherits from
     * {@code Object.prototype} reaches its accessor instead of creating a property (prototypes are not modelled, so
     * the assignment has no further effect).
     *
     * @param key the key
     * @param value the value
     */
    public void set(String key, Object value) {
        if (!nullPrototype && "__proto__".equals(key) && !properties.containsKey(key)) {
            return;
        }
        defineOwn(key, value);
    }

    /**
     * Creates or overwrites an own data property, as JSON.parse, object literals with computed keys and spreading do.
     *
     * @param key the key
     * @param value the value
     */
    public void defineOwn(String key, Object value) {
        if (!properties.containsKey(key) && isArrayIndex(key)) {
            indexKeyCount++;
        }
        properties.put(key, value);
    }

    /**
     * {@code delete object[key]}.
     *
     * @param key the key
     */
    public void delete(String key) {
        if (properties.containsKey(key)) {
            properties.remove(key);
            if (isArrayIndex(key)) {
                indexKeyCount--;
            }
        }
    }

    /**
     * {@code Object.keys(object)}, in JavaScript's order.
     *
     * @return the own keys
     */
    public List<String> keys() {
        List<String> keys = new ArrayList<>(properties.keySet());
        if (indexKeyCount == 0) {
            return keys;
        }
        List<String> ordered = new ArrayList<>(keys.size());
        keys.stream().filter(JsObject::isArrayIndex).sorted((a, b) -> Long.compare(Long.parseLong(a),
                Long.parseLong(b))).forEach(ordered::add);
        keys.stream().filter(key -> !isArrayIndex(key)).forEach(ordered::add);
        return ordered;
    }

    /**
     * The number of own properties.
     *
     * @return {@code Object.keys(object).length}
     */
    public int size() {
        return properties.size();
    }

    /**
     * The value stored under the parser's metadata symbol.
     *
     * @return the metadata, undefined when there is none
     */
    public Object metadata() {
        return metadata;
    }

    /**
     * Stores a value under the parser's metadata symbol.
     *
     * @param value the metadata
     */
    public void setMetadata(Object value) {
        metadata = value;
    }

    /**
     * A shallow copy with the same own properties, as {@code {...object}} makes (without the metadata symbol).
     *
     * @return the copy
     */
    public JsObject copy() {
        JsObject copy = new JsObject();
        for (String key : keys()) {
            copy.defineOwn(key, properties.get(key));
        }
        return copy;
    }

    /** The members of {@code Object.prototype} a property read can reach. */
    static final class ObjectPrototype {
        /** {@code Object.prototype} itself, the value of {@code __proto__}. */
        static final JsHost PROTOTYPE = new JsHost() {
        };

        private static final Map<String, NativeFunction> MEMBERS = Map.ofEntries(
                Map.entry("constructor", new NativeFunction("Object", 1)),
                Map.entry("toString", new NativeFunction("toString", 0)),
                Map.entry("toLocaleString", new NativeFunction("toLocaleString", 0)),
                Map.entry("valueOf", new NativeFunction("valueOf", 0)),
                Map.entry("hasOwnProperty", new NativeFunction("hasOwnProperty", 1)),
                Map.entry("isPrototypeOf", new NativeFunction("isPrototypeOf", 1)),
                Map.entry("propertyIsEnumerable", new NativeFunction("propertyIsEnumerable", 1)),
                Map.entry("__defineGetter__", new NativeFunction("__defineGetter__", 2)),
                Map.entry("__defineSetter__", new NativeFunction("__defineSetter__", 2)),
                Map.entry("__lookupGetter__", new NativeFunction("__lookupGetter__", 1)),
                Map.entry("__lookupSetter__", new NativeFunction("__lookupSetter__", 1)));

        private ObjectPrototype() {
        }

        static Object member(String key) {
            return "__proto__".equals(key) ? PROTOTYPE : MEMBERS.get(key);
        }
    }

    /** A built-in function reached by a property read; its string form is V8's native-code text. */
    record NativeFunction(String name, int arity) implements JsFunction {
        @Override
        public Object call(Object... args) {
            return Undefined.INSTANCE;
        }

        @Override
        public int length() {
            return arity;
        }

        @Override
        public String toString() {
            return "function " + name + "() { [native code] }";
        }
    }
}
