// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.cli;

import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsFunction;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsString;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.pathexpr.PathView;

/**
 * The callbacks a request describes with {@code {"$fn": SPEC}}: results chosen by path, expression, name or default,
 * evaluated per call, and the call log of the response.
 */
final class Callbacks {
    private final JsArray log = new JsArray();
    private boolean enabled;

    /**
     * Whether any callback of the request logs its calls.
     *
     * @return true when the response carries "calls"
     */
    boolean enabled() {
        return enabled;
    }

    /**
     * The call log.
     *
     * @return one {@code [label, args...]} entry per logged call
     */
    JsArray log() {
        return log;
    }

    private static boolean isObject(Object value) {
        return value instanceof JsObject;
    }

    private static boolean hasOwn(Object object, String key) {
        return object instanceof JsObject o && o.hasOwn(key);
    }

    private static Object argAt(Object[] args, Object index) {
        if (index instanceof Double d && d >= 0 && d == Math.floor(d) && d < args.length) {
            return args[d.intValue()];
        }
        return Js.UNDEFINED;
    }

    /**
     * The function a {@code $fn} shape stands for.
     *
     * @param spec the SPEC object
     * @param label the key of the option member holding it
     * @return the function
     * @throws JsError a RequestError when SPEC is not an object
     */
    JsFunction create(Object spec, String label) {
        if (!(spec instanceof JsObject s)) {
            throw new JsError("RequestError", "$fn must be an object");
        }
        if (Js.truthy(s.get("log"))) {
            enabled = true;
        }
        Object pathArg = s.hasOwn("pathArg") ? s.get("pathArg") : (Object) 1.0;
        return args -> {
            if (Js.truthy(s.get("log"))) {
                JsArray entry = new JsArray();
                entry.push(label);
                for (Object arg : args) {
                    entry.push(describe(arg));
                }
                log.push(entry);
            }
            Object path = argAt(args, pathArg);
            String pathKey = path instanceof String text ? text : path instanceof PathView view ? view.toString() : null;
            Object byPath = s.get("byPath");
            if (pathKey != null && isObject(byPath) && hasOwn(byPath, pathKey)) {
                return evaluate(Js.get(byPath, pathKey), args);
            }
            Object byExpr = s.get("byExpr");
            if (path instanceof PathView view && byExpr instanceof JsArray rules) {
                for (Object rule : rules) {
                    Object pattern = Js.get(rule, 0);
                    if (view.matches(new Expression(Js.toStr(pattern)))) {
                        return evaluate(Js.get(rule, 1), args);
                    }
                }
            }
            Object byName = s.get("byName");
            String name = Js.toStr(args.length > 0 ? args[0] : Js.UNDEFINED);
            if (isObject(byName) && hasOwn(byName, name)) {
                return evaluate(Js.get(byName, name), args);
            }
            return s.hasOwn("else") ? evaluate(s.get("else"), args) : Js.UNDEFINED;
        };
    }

    /**
     * The value of a callback result for one call.
     *
     * @param result the result description
     * @param args the call's arguments
     * @return the value
     */
    static Object evaluate(Object result, Object[] args) {
        if (result instanceof JsArray array) {
            JsArray out = new JsArray();
            for (Object member : array) {
                out.push(evaluate(member, args));
            }
            return out;
        }
        if (!(result instanceof JsObject r)) {
            return result;
        }
        if (r.hasOwn("$arg")) {
            return argAt(args, r.get("$arg"));
        }
        if (r.hasOwn("$undefined")) {
            return Js.UNDEFINED;
        }
        if (r.hasOwn("$throw")) {
            Object message = r.get("$throw");
            throw JsError.error(message == Js.UNDEFINED ? "" : Js.toStr(message));
        }
        if (r.hasOwn("$concat")) {
            Object[] text = {""};
            Js.iterate(r.get("$concat"), part -> text[0] = Js.add(text[0], evaluate(part, args)));
            return text[0];
        }
        if (r.hasOwn("$lower")) {
            return JsString.toLowerCase(Js.toStr(argAt(args, r.get("$lower"))));
        }
        if (r.hasOwn("$upper")) {
            return JsString.toUpperCase(Js.toStr(argAt(args, r.get("$upper"))));
        }
        if (r.hasOwn("$set") || r.hasOwn("$delete")) {
            Object target = argAt(args, r.hasOwn("$on") ? r.get("$on") : (Object) 2.0);
            Object set = r.get("$set");
            if (!Js.isNullish(set)) {
                for (String key : Js.keys(set)) {
                    Js.set(target, key, evaluate(Js.get(set, key), args));
                }
            }
            Js.iterate(r.get("$delete"), key -> Js.delete(target, Js.toStr(key)));
            return r.hasOwn("$return") ? evaluate(r.get("$return"), args) : Js.UNDEFINED;
        }
        JsObject out = new JsObject();
        for (String key : r.keys()) {
            out.defineOwn(key, evaluate(r.get(key), args));
        }
        return out;
    }

    /**
     * An argument as the call log shows it, copied at the time of the call.
     *
     * @param value the argument
     * @return its description
     */
    static Object describe(Object value) {
        if (value == Js.UNDEFINED) {
            JsObject undefined = new JsObject();
            undefined.set("$undefined", true);
            return undefined;
        }
        if (value instanceof PathView matcher) {
            JsObject state = new JsObject();
            state.set("path", matcher.toString());
            state.set("tag", matcher.getCurrentTag());
            state.set("namespace", matcher.getCurrentNamespace());
            state.set("depth", matcher.getDepth());
            state.set("position", matcher.getPosition());
            state.set("counter", matcher.getCounter());
            JsObject described = new JsObject();
            described.set("$matcher", state);
            return described;
        }
        if (value instanceof JsArray array) {
            JsArray out = new JsArray();
            for (Object member : array) {
                out.push(describe(member));
            }
            return out;
        }
        if (value instanceof JsObject object) {
            JsObject out = new JsObject();
            for (String key : object.keys()) {
                out.defineOwn(key, describe(object.get(key)));
            }
            return out;
        }
        return value;
    }
}
