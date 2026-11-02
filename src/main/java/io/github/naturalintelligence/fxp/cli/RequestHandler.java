// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp.cli;

import io.github.naturalintelligence.fxp.builder.XMLBuilder;
import io.github.naturalintelligence.fxp.entities.EntityDecoder;
import io.github.naturalintelligence.fxp.js.Js;
import io.github.naturalintelligence.fxp.js.JsArray;
import io.github.naturalintelligence.fxp.js.JsError;
import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.JsRegExp;
import io.github.naturalintelligence.fxp.js.Json;
import io.github.naturalintelligence.fxp.parser.XMLParser;
import io.github.naturalintelligence.fxp.pathexpr.Expression;
import io.github.naturalintelligence.fxp.validator.XMLValidator;

/** Answers one request of the fast-xml-parser command (see {@link Main} for the protocol). */
final class RequestHandler {
    private final Callbacks callbacks = new Callbacks();

    private RequestHandler() {
    }

    static JsError requestError(String message) {
        return new JsError("RequestError", message);
    }

    /**
     * The response to a parsed request line.
     *
     * @param request the request value
     * @return the response object
     * @throws JsError a RequestError for a bad request, or whatever the library throws
     */
    static JsObject handle(Object request) {
        if (!(request instanceof JsObject r)) {
            throw requestError("a request must be a JSON object");
        }
        return new RequestHandler().dispatch(r);
    }

    private JsObject dispatch(JsObject request) {
        Object op = request.get("op");
        String name = op instanceof String s ? s : "";
        switch (name) {
            case "parse": {
                Object options = revive(request.get("options"), "options");
                Object input = parseInput(request);
                JsArray entities = entityList(request);
                XMLParser parser = new XMLParser(options);
                for (Object pair : entities) {
                    parser.addEntity(Js.get(pair, 0), Js.get(pair, 1));
                }
                return withCalls(result(parser.parse(input, request.get("validate"))));
            }
            case "validate": {
                if (!(request.get("xml") instanceof String xml)) {
                    throw requestError("xml must be a string");
                }
                return result(XMLValidator.validate(xml, revive(request.get("options"), "options")));
            }
            case "build": {
                XMLBuilder builder = new XMLBuilder(revive(request.get("options"), "options"));
                return withCalls(result(builder.build(request.get("data"))));
            }
            case "cli":
                return FxParserCommand.run(request);
            default: {
                String shown = Json.stringify(op);
                throw requestError("unknown op: " + (shown == null ? "undefined" : shown));
            }
        }
    }

    private static JsObject result(Object value) {
        JsObject response = new JsObject();
        response.set("result", value);
        return response;
    }

    private JsObject withCalls(JsObject response) {
        if (callbacks.enabled()) {
            response.set("calls", callbacks.log());
        }
        return response;
    }

    private static Object parseInput(JsObject request) {
        Object bytes = request.get("bytes");
        if (bytes == Js.UNDEFINED) {
            return request.get("xml");
        }
        boolean valid = bytes instanceof JsArray array && array.items().stream().allMatch(b -> b instanceof Double d
                && d >= 0 && d <= 255 && d == Math.floor(d));
        if (!valid) {
            throw requestError("bytes must be an array of integers from 0 to 255");
        }
        JsArray array = (JsArray) bytes;
        byte[] data = new byte[array.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((Double) array.get(i)).intValue();
        }
        return data;
    }

    private static JsArray entityList(JsObject request) {
        Object entities = request.get("entities");
        if (entities == Js.UNDEFINED) {
            return new JsArray();
        }
        boolean valid = entities instanceof JsArray array
                && array.items().stream().allMatch(pair -> pair instanceof JsArray p && p.size() == 2);
        if (!valid) {
            throw requestError("entities must be an array of [name, value] pairs");
        }
        return (JsArray) entities;
    }

    // The options value with its $fn, $regex, $expression and $decoder shapes revived.
    private Object revive(Object value, String label) {
        if (value instanceof JsArray array) {
            JsArray out = new JsArray();
            for (Object member : array) {
                out.push(revive(member, label));
            }
            return out;
        }
        if (!(value instanceof JsObject object)) {
            return value;
        }
        if (object.hasOwn("$fn")) {
            return callbacks.create(object.get("$fn"), label);
        }
        if (object.hasOwn("$regex")) {
            if (!(object.get("$regex") instanceof String source)) {
                throw requestError("$regex source must be a string");
            }
            Object flags = object.hasOwn("$flags") ? object.get("$flags") : "";
            if (!(flags instanceof String flagText)) {
                throw requestError("$flags must be a string");
            }
            return JsRegExp.create(source, flagText);
        }
        if (object.hasOwn("$expression")) {
            if (!(object.get("$expression") instanceof String pattern)) {
                throw requestError("$expression must be a string");
            }
            return new Expression(pattern, object.get("$separator"));
        }
        if (object.hasOwn("$decoder")) {
            if (!(object.get("$decoder") instanceof JsObject)) {
                throw requestError("$decoder must be an object");
            }
            EntityDecoder decoder = new EntityDecoder(revive(object.get("$decoder"), label));
            if (object.hasOwn("$external")) {
                decoder.setExternalEntities(revive(object.get("$external"), label));
            }
            return decoder;
        }
        JsObject out = new JsObject();
        for (String key : object.keys()) {
            out.defineOwn(key, revive(object.get(key), key));
        }
        return out;
    }
}
