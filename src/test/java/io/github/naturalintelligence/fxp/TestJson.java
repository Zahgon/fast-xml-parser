// SPDX-License-Identifier: MIT
// Copyright (c) 2017 Amit Kumar Gupta (github.com/NaturalIntelligence/fast-xml-parser)

package io.github.naturalintelligence.fxp;

import io.github.naturalintelligence.fxp.js.JsObject;
import io.github.naturalintelligence.fxp.js.Json;

/** Helpers for tests: options written as JSON text, results compared as JSON text. */
public final class TestJson {
    private TestJson() {
    }

    /**
     * An options object from JSON text.
     *
     * @param json the options as JSON
     * @return the object
     */
    public static JsObject options(String json) {
        return (JsObject) Json.parse(json);
    }

    /**
     * A value as JSON.stringify writes it.
     *
     * @param value the value
     * @return its JSON text
     */
    public static String json(Object value) {
        return Json.stringify(value);
    }
}
