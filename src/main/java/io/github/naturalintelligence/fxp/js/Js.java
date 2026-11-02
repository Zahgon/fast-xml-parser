// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.js;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * JavaScript semantics over the value model: {@link Undefined}, {@code null}, {@link Boolean}, {@link Double},
 * {@link String}, {@link JsObject}, {@link JsArray}, {@link JsFunction}, {@link JsRegExp} and {@link JsHost} objects.
 */
public final class Js {
    /** JavaScript's {@code undefined}. */
    public static final Object UNDEFINED = Undefined.INSTANCE;

    private Js() {
    }

    /**
     * {@code typeof value}.
     *
     * @param value any value
     * @return the type name
     */
    public static String typeOf(Object value) {
        if (value == UNDEFINED) {
            return "undefined";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Double) {
            return "number";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof JsFunction) {
            return "function";
        }
        return "object";
    }

    /**
     * {@code Boolean(value)}.
     *
     * @param value any value
     * @return whether the value is truthy
     */
    public static boolean truthy(Object value) {
        if (value == null || value == UNDEFINED) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Double d) {
            return d != 0 && !d.isNaN();
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    /**
     * Whether a value is {@code null} or {@code undefined} ({@code value == null}).
     *
     * @param value any value
     * @return true for null and undefined
     */
    public static boolean isNullish(Object value) {
        return value == null || value == UNDEFINED;
    }

    /**
     * {@code a === b}.
     *
     * @param a a value
     * @param b a value
     * @return whether the values are strictly equal
     */
    public static boolean strictEquals(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x.doubleValue() == y.doubleValue();
        }
        if (a instanceof String || a instanceof Boolean) {
            return a.equals(b);
        }
        return a == b;
    }

    /**
     * A number value.
     *
     * @param value the number
     * @return the boxed double
     */
    public static Double num(double value) {
        return value;
    }

    /**
     * {@code ToPrimitive(value)}; objects become strings the way JavaScript converts them.
     *
     * @param value any value
     * @return a primitive
     */
    public static Object toPrimitive(Object value) {
        if (value instanceof JsObject object) {
            Object toString = object.get("toString");
            if (object.hasOwn("toString")) {
                if (toString instanceof JsFunction function) {
                    Object result = function.call();
                    if (!(result instanceof JsObject || result instanceof JsArray || result instanceof JsHost)) {
                        return result;
                    }
                }
                throw JsError.typeError("Cannot convert object to primitive value");
            }
            return "[object Object]";
        }
        if (value instanceof JsArray array) {
            return join(array, ",");
        }
        if (value instanceof JsHost host) {
            return host.jsToString();
        }
        if (value instanceof JsRegExp regexp) {
            return regexp.toString();
        }
        if (value instanceof JsFunction) {
            return value.toString();
        }
        return value;
    }

    /**
     * {@code String(value)} / {@code ToString(value)}.
     *
     * @param value any value
     * @return the string
     */
    public static String toStr(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value == null) {
            return "null";
        }
        if (value == UNDEFINED) {
            return "undefined";
        }
        if (value instanceof Double d) {
            return JsNumber.toString(d);
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        Object primitive = toPrimitive(value);
        return primitive == value ? "[object Object]" : toStr(primitive);
    }

    /**
     * {@code ToNumber(value)}.
     *
     * @param value any value
     * @return the number
     */
    public static double toNumber(Object value) {
        if (value instanceof Double d) {
            return d;
        }
        if (value == null) {
            return 0;
        }
        if (value == UNDEFINED) {
            return Double.NaN;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof String s) {
            return JsNumber.stringToNumber(s);
        }
        Object primitive = toPrimitive(value);
        return primitive == value ? Double.NaN : toNumber(primitive);
    }

    /**
     * {@code a + b}.
     *
     * @param a the left operand
     * @param b the right operand
     * @return the concatenation when either primitive is a string, else the numeric sum
     */
    public static Object add(Object a, Object b) {
        Object left = toPrimitive(a);
        Object right = toPrimitive(b);
        if (left instanceof String || right instanceof String) {
            return toStr(left) + toStr(right);
        }
        return toNumber(left) + toNumber(right);
    }

    /**
     * {@code Array.prototype.join}.
     *
     * @param array the array
     * @param separator the separator
     * @return the joined string; null and undefined elements are empty
     */
    public static String join(JsArray array, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            Object element = array.get(i);
            if (!isNullish(element)) {
                out.append(toStr(element));
            }
        }
        return out.toString();
    }

    private static String describeForRead(Object target) {
        return target == null ? "null" : "undefined";
    }

    /**
     * {@code target[key]}.
     *
     * @param target the object read from
     * @param key the property key
     * @return the value, undefined when absent
     * @throws JsError a TypeError when target is null or undefined
     */
    public static Object get(Object target, String key) {
        if (isNullish(target)) {
            throw JsError.typeError("Cannot read properties of " + describeForRead(target) + " (reading '" + key
                    + "')");
        }
        if (target instanceof JsObject object) {
            return object.get(key);
        }
        if (target instanceof JsArray array) {
            if ("length".equals(key)) {
                return (double) array.size();
            }
            return JsObject.isArrayIndex(key) ? array.get(listIndex(key)) : UNDEFINED;
        }
        if (target instanceof String s) {
            if ("length".equals(key)) {
                return (double) s.length();
            }
            return JsObject.isArrayIndex(key) && key.length() < 10 ? JsString.charAt(s, Integer.parseInt(key))
                    : UNDEFINED;
        }
        if (target instanceof JsFunction function && "length".equals(key)) {
            return (double) function.length();
        }
        return UNDEFINED;
    }

    /**
     * {@code target[index]}.
     *
     * @param target the object read from
     * @param index the index
     * @return the value
     */
    public static Object get(Object target, int index) {
        return get(target, Integer.toString(index));
    }

    /**
     * {@code target[key] = value}.
     *
     * @param target the object written to
     * @param key the property key
     * @param value the value
     * @throws JsError a TypeError when target is null or undefined
     */
    public static void set(Object target, String key, Object value) {
        if (isNullish(target)) {
            throw JsError.typeError("Cannot set properties of " + describeForRead(target) + " (setting '" + key
                    + "')");
        }
        if (target instanceof JsObject object) {
            object.set(key, value);
        } else if (target instanceof JsArray array && JsObject.isArrayIndex(key)) {
            int index = listIndex(key);
            if (index == Integer.MAX_VALUE) {
                throw new JsError("RangeError", "Invalid array length");
            }
            while (array.size() <= index) {
                array.push(UNDEFINED);
            }
            array.set(index, value);
        }
    }

    /**
     * {@code delete target[key]}.
     *
     * @param target the object
     * @param key the property key
     * @throws JsError a TypeError when target is null or undefined
     */
    public static void delete(Object target, String key) {
        if (isNullish(target)) {
            throw JsError.typeError("Cannot convert undefined or null to object");
        }
        if (target instanceof JsObject object) {
            object.delete(key);
        }
    }

    /**
     * {@code Object.keys(value)}.
     *
     * @param value any value
     * @return the own enumerable string keys in JavaScript's order
     * @throws JsError a TypeError for null and undefined
     */
    public static List<String> keys(Object value) {
        if (isNullish(value)) {
            throw JsError.typeError("Cannot convert undefined or null to object");
        }
        return forInKeys(value);
    }

    /**
     * The keys {@code for (key in value)} visits (none for null and undefined).
     *
     * @param value any value
     * @return the enumerable string keys in JavaScript's order
     */
    public static List<String> forInKeys(Object value) {
        if (value instanceof JsObject object) {
            return object.keys();
        }
        int count = value instanceof JsArray array ? array.size() : value instanceof String s ? s.length() : 0;
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(Integer.toString(i));
        }
        return keys;
    }

    /**
     * {@code Object.prototype.hasOwnProperty.call(value, key)}.
     *
     * @param value any value
     * @param key the key
     * @return whether the value has that own property
     * @throws JsError a TypeError for null and undefined
     */
    public static boolean hasOwn(Object value, String key) {
        if (isNullish(value)) {
            throw JsError.typeError("Cannot convert undefined or null to object");
        }
        if (value instanceof JsObject object) {
            return object.hasOwn(key);
        }
        if (value instanceof JsArray array) {
            return JsObject.isArrayIndex(key) && Long.parseLong(key) < array.size();
        }
        if (value instanceof String s) {
            return JsObject.isArrayIndex(key) && Long.parseLong(key) < s.length() || "length".equals(key);
        }
        return false;
    }

    /**
     * {@code key in value} for an object value.
     *
     * @param key the key
     * @param value the object
     * @return whether the object has or inherits the property
     */
    public static boolean in(String key, Object value) {
        if (value instanceof JsObject object) {
            return object.has(key);
        }
        if (value instanceof JsArray array) {
            return "length".equals(key) || JsObject.isArrayIndex(key) && Long.parseLong(key) < array.size();
        }
        if (!(value instanceof JsHost || value instanceof JsFunction || value instanceof JsRegExp)) {
            throw JsError.typeError("Cannot use 'in' operator to search for '" + key + "' in " + toStr(value));
        }
        return false;
    }

    /**
     * Calls a value that must be a function.
     *
     * @param callee the value called
     * @param calleeText the call expression's text, which V8 quotes when the value is not a function
     * @param args the arguments
     * @return the result
     * @throws JsError a TypeError when the value is not a function
     */
    public static Object call(Object callee, String calleeText, Object... args) {
        if (callee instanceof JsFunction function) {
            return function.call(args);
        }
        throw JsError.typeError(calleeText + " is not a function");
    }

    /**
     * Iterates a value the way {@code new Set(value)} does: nothing for null and undefined, the code points of a
     * string, the elements of an array.
     *
     * @param value the iterable
     * @param action called for each element
     * @throws JsError a TypeError when the value is not iterable
     */
    public static void iterate(Object value, Consumer<Object> action) {
        if (isNullish(value)) {
            return;
        }
        if (value instanceof JsArray array) {
            new ArrayList<>(array.items()).forEach(action);
        } else if (value instanceof String s) {
            s.codePoints().forEach(cp -> action.accept(new String(Character.toChars(cp))));
        } else {
            String shown = value instanceof Double || value instanceof Boolean ? typeOf(value) + " " + toStr(value)
                    : typeOf(value);
            throw JsError.typeError(shown + " is not iterable (cannot read property Symbol(Symbol.iterator))");
        }
    }

    /**
     * {@code {...target, ...source}} for one source: copies its own enumerable properties as data properties.
     *
     * @param target the object being built
     * @param source any value (null, undefined and other primitives without properties add nothing)
     */
    public static void spreadInto(JsObject target, Object source) {
        if (isNullish(source)) {
            return;
        }
        for (String key : forInKeys(source)) {
            target.defineOwn(key, get(source, key));
        }
    }

    // An array index goes up to 4294967294; one past what a Java list can hold becomes Integer.MAX_VALUE, which is never a position in a list.
    private static int listIndex(String key) {
        return (int) Math.min(Long.parseLong(key), Integer.MAX_VALUE);
    }

    /**
     * {@code Object.assign(target, source)} for one source: copies its own enumerable properties by assignment.
     *
     * @param target the target
     * @param source any value (null, undefined and other primitives without properties add nothing)
     */
    public static void assign(JsObject target, Object source) {
        if (isNullish(source)) {
            return;
        }
        for (String key : forInKeys(source)) {
            target.set(key, get(source, key));
        }
    }

    /**
     * {@code a > b} (the abstract relational comparison).
     *
     * @param a the left operand
     * @param b the right operand
     * @return the comparison result, false when either side is NaN
     */
    public static boolean greaterThan(Object a, Object b) {
        Object left = toPrimitive(a);
        Object right = toPrimitive(b);
        if (left instanceof String x && right instanceof String y) {
            return x.compareTo(y) > 0;
        }
        return toNumber(left) > toNumber(right);
    }

    /**
     * {@code a >= b} (false when either side is NaN).
     *
     * @param a the left operand
     * @param b the right operand
     * @return the comparison result
     */
    public static boolean greaterOrEqual(Object a, Object b) {
        Object left = toPrimitive(a);
        Object right = toPrimitive(b);
        if (left instanceof String x && right instanceof String y) {
            return x.compareTo(y) >= 0;
        }
        return toNumber(left) >= toNumber(right);
    }

    /**
     * {@code Math.max(a, b)}.
     *
     * @param a a value
     * @param b a value
     * @return the larger number, NaN when either is NaN
     */
    public static double max(Object a, Object b) {
        double x = toNumber(a);
        double y = toNumber(b);
        return Double.isNaN(x) || Double.isNaN(y) ? Double.NaN : Math.max(x, y);
    }

    /**
     * {@code s.startsWith(search)}.
     *
     * @param s the string
     * @param search the value searched for, converted to a string
     * @return whether s starts with it
     * @throws JsError a TypeError when search is a regular expression
     */
    public static boolean startsWith(String s, Object search) {
        if (search instanceof JsRegExp) {
            throw JsError.typeError("First argument to String.prototype.startsWith must not be a regular expression");
        }
        return s.startsWith(toStr(search));
    }

    /**
     * {@code s.substring(start)} for any start value (converted as JavaScript converts it, then clamped).
     *
     * @param s the string
     * @param start the start value
     * @return the substring
     */
    public static String substringFrom(String s, Object start) {
        double index = toNumber(start);
        if (Double.isNaN(index) || index < 0) {
            return s;
        }
        return index >= s.length() ? "" : s.substring((int) index);
    }

    /**
     * {@code Array.isArray(value)}.
     *
     * @param value any value
     * @return true for arrays
     */
    public static boolean isArray(Object value) {
        return value instanceof JsArray;
    }

    /**
     * Whether a value is an object and not null ({@code typeof value === 'object' && value !== null}).
     *
     * @param value any value
     * @return true for objects, arrays and host objects
     */
    public static boolean isObjectValue(Object value) {
        return value != null && "object".equals(typeOf(value));
    }
}
